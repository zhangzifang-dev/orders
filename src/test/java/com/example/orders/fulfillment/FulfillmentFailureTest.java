package com.example.orders.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.orders.fulfillment.application.FulfillmentQueryService;
import com.example.orders.fulfillment.application.port.BillingOutcome;
import com.example.orders.fulfillment.application.port.BillingPort;
import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sales.application.CreateOrderCommand;
import com.example.orders.sales.application.CreateOrderLineCommand;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.FulfillmentFailedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OutboxRelay;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * 履约异常路径测试：后置步骤抛出非业务异常时，流程必须落到终态并写出失败事件。
 */
@SpringBootTest
@ActiveProfiles("test")
class FulfillmentFailureTest {

	@Autowired
	private OrderApplicationService orderApi;

	@Autowired
	private FulfillmentQueryService fulfillmentApi;

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void unexpectedStepFailureLeavesProcessTerminalAndWritesFailedEvent() {
		Sku sku = Sku.of("SKU-" + UUID.randomUUID());
		stockItemRepository.save(StockItem.create(sku, "机械键盘", 0));
		inventoryService.restock(sku, 5);

		OrderId id = orderApi.create(new CreateOrderCommand(CustomerId.of(UUID.randomUUID()), Currency.CNY,
				List.of(new CreateOrderLineCommand(sku, "机械键盘", Money.of("199.00", Currency.CNY),
						Quantity.of(2)))));

		orderApi.place(id);
		relay.relayOnce();

		assertThat(fulfillmentApi.get(id).status()).isEqualTo("FAILED");
		assertThat(orderApi.get(id).status()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(failedEventCount(id)).isEqualTo(1);
	}

	private long failedEventCount(OrderId orderId) {
		return jdbcClient.sql("""
				SELECT COUNT(*) FROM outbox_event
				WHERE event_type = :eventType AND aggregate_id = :aggregateId
				""")
			.param("eventType", FulfillmentFailedIntegrationEvent.EVENT_TYPE)
			.param("aggregateId", orderId.value().toString())
			.query(Long.class)
			.single();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingBillingConfig {

		@Bean
		@Primary
		BillingPort failingBillingPort() {
			return new BillingPort() {

				@Override
				public BillingOutcome issueAndCapture(OrderId orderId, CustomerId customerId, Money amount) {
					throw new IllegalStateException("计费服务不可用");
				}

			};
		}

	}

}
