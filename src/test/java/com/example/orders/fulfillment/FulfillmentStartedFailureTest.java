package com.example.orders.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.orders.fulfillment.application.FulfillmentQueryService;
import com.example.orders.fulfillment.application.port.SalesPort;
import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.application.InventoryQueryService;
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
 * {@code STARTED} 路径的失败测试：库存不足且取消订单也失败时，流程仍须落到 {@code FAILED}
 * 并写出失败事件，库存不能被留在非终态。
 */
@SpringBootTest
@ActiveProfiles("test")
class FulfillmentStartedFailureTest {

	@Autowired
	private OrderApplicationService orderApi;

	@Autowired
	private FulfillmentQueryService fulfillmentApi;

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private InventoryQueryService inventoryApi;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private ThrowingSalesPort salesPort;

	@Test
	void stockShortageWithCancelFailureStillFailsProcessAndWritesEvent() {
		Sku sku = Sku.of("SKU-" + UUID.randomUUID());
		stockItemRepository.save(StockItem.create(sku, "机械键盘", 0));
		inventoryService.restock(sku, 1);

		OrderId id = orderApi.create(new CreateOrderCommand(CustomerId.of(UUID.randomUUID()), Currency.CNY,
				List.of(new CreateOrderLineCommand(sku, "机械键盘", Money.of("199.00", Currency.CNY),
						Quantity.of(2)))));
		orderApi.place(id);

		relay.relayOnce();

		assertThat(salesPort.cancelAttempts()).isEqualTo(1);
		assertThat(fulfillmentApi.get(id).status()).isEqualTo("FAILED");
		assertThat(inventoryApi.get(sku).onHand()).isEqualTo(1);
		assertThat(inventoryApi.get(sku).reserved()).isZero();
		assertThat(inventoryApi.get(sku).available()).isEqualTo(1);
		assertThat(orderApi.get(id).status()).isEqualTo(OrderStatus.PLACED);
		assertThat(failedEventPayload(id)).contains("库存不足").contains("取消订单失败");
	}

	private String failedEventPayload(OrderId orderId) {
		List<String> payloads = jdbcClient.sql("""
				SELECT payload FROM outbox_event
				WHERE event_type = :eventType AND aggregate_id = :aggregateId
				""")
			.param("eventType", FulfillmentFailedIntegrationEvent.EVENT_TYPE)
			.param("aggregateId", orderId.value().toString())
			.query(String.class)
			.list();
		assertThat(payloads).hasSize(1);
		return payloads.getFirst();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingCancelConfig {

		@Bean
		@Primary
		ThrowingSalesPort throwingSalesPort(OrderApplicationService orderApplicationService) {
			return new ThrowingSalesPort(orderApplicationService);
		}

	}

	static final class ThrowingSalesPort implements SalesPort {

		private final OrderApplicationService orderApplicationService;

		private int cancelAttempts;

		ThrowingSalesPort(OrderApplicationService orderApplicationService) {
			this.orderApplicationService = orderApplicationService;
		}

		@Override
		public void confirm(OrderId orderId) {
			orderApplicationService.confirmOrder(orderId);
		}

		@Override
		public void cancel(OrderId orderId, String reason) {
			cancelAttempts++;
			throw new IllegalStateException("销售服务不可用");
		}

		int cancelAttempts() {
			return cancelAttempts;
		}

	}

}
