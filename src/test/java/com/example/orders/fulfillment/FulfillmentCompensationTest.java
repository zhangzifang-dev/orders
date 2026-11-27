package com.example.orders.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.orders.billing.domain.Captured;
import com.example.orders.billing.domain.Declined;
import com.example.orders.billing.support.FakePaymentGateway;
import com.example.orders.fulfillment.application.FulfillmentQueryService;
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
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * 履约补偿路径的端到端测试：真实 Spring 上下文、真实服务与 H2，不使用 Mockito。
 *
 * <p>库存不足走 {@code FAILED}，支付失败先释放库存再取消订单并走 {@code CANCELLED}，
 * 重复投递的 {@code OrderPlaced} 只预占一次。
 */
@SpringBootTest
@ActiveProfiles("test")
class FulfillmentCompensationTest {

	@Autowired
	private OrderApplicationService orderApi;

	@Autowired
	private FulfillmentQueryService fulfillmentApi;

	@Autowired
	private InventoryQueryService inventoryApi;

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private FakePaymentGateway paymentGateway;

	@BeforeEach
	void resetPaymentGateway() {
		paymentGateway.reset();
	}

	@Test
	void stockShortageCancelsOrder() {
		Sku sku = newSku();
		givenStock(sku, 1);

		OrderId id = placeOrder(sku, Quantity.of(2));

		relay.relayOnce();

		assertThat(orderApi.get(id).status()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(fulfillmentApi.get(id).status()).isEqualTo("FAILED");
		assertThat(inventoryApi.get(sku).onHand()).isEqualTo(1);
		assertThat(inventoryApi.get(sku).reserved()).isZero();
		assertThat(inventoryApi.get(sku).available()).isEqualTo(1);
		assertThat(failedEventCount(id)).isEqualTo(1);
	}

	@Test
	void paymentFailureReleasesStockAndCancelsOrder() {
		paymentGateway.enqueue(new Declined("余额不足"));
		Sku sku = newSku();
		givenStock(sku, 5);
		int originalAvailable = inventoryApi.get(sku).available();

		OrderId id = placeOrder(sku, Quantity.of(2));

		relay.relayOnce();

		assertThat(orderApi.get(id).status()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(fulfillmentApi.get(id).status()).isEqualTo("CANCELLED");
		assertThat(fulfillmentApi.get(id).failureReason()).isEqualTo("余额不足");
		assertThat(inventoryApi.get(sku).onHand()).isEqualTo(5);
		assertThat(inventoryApi.get(sku).reserved()).isZero();
		assertThat(inventoryApi.get(sku).available()).isEqualTo(originalAvailable);
		assertThat(failedEventCount(id)).isEqualTo(1);
	}

	@Test
	void duplicateOrderPlacedEventDoesNotReserveTwice() {
		paymentGateway.enqueue(new Captured("gateway-ref"));
		Sku sku = newSku();
		givenStock(sku, 5);

		OrderId id = placeOrder(sku, Quantity.of(2));
		duplicatePlacedEvent(id);

		relay.relayOnce();

		assertThat(inventoryApi.get(sku).reserved()).isEqualTo(2);
		assertThat(inventoryApi.get(sku).available()).isEqualTo(3);
		assertThat(paymentGateway.capturedRequests()).hasSize(1);
		assertThat(fulfillmentApi.get(id).status()).isEqualTo("COMPLETED");
		assertThat(orderApi.get(id).status()).isEqualTo(OrderStatus.CONFIRMED);
	}

	private Sku newSku() {
		return Sku.of("SKU-" + UUID.randomUUID());
	}

	private void givenStock(Sku sku, int onHand) {
		stockItemRepository.save(StockItem.create(sku, "机械键盘", 0));
		inventoryService.restock(sku, onHand);
	}

	private OrderId placeOrder(Sku sku, Quantity quantity) {
		OrderId id = orderApi.create(new CreateOrderCommand(CustomerId.of(UUID.randomUUID()), Currency.CNY,
				List.of(new CreateOrderLineCommand(sku, "机械键盘", Money.of("199.00", Currency.CNY), quantity))));
		orderApi.place(id);
		return id;
	}

	/**
	 * 复制 outbox 中的 {@code OrderPlaced} 行（保持相同 payload/eventId），模拟消息中间件的重复投递。
	 */
	private void duplicatePlacedEvent(OrderId orderId) {
		int copied = jdbcClient.sql("""
				INSERT INTO outbox_event (id, event_type, aggregate_type, aggregate_id, payload, occurred_at, published_at)
				SELECT :newId, event_type, aggregate_type, aggregate_id, payload, occurred_at, NULL
				FROM outbox_event
				WHERE event_type = :eventType AND aggregate_id = :aggregateId
				""")
			.param("newId", UUID.randomUUID())
			.param("eventType", OrderPlacedIntegrationEvent.EVENT_TYPE)
			.param("aggregateId", orderId.value().toString())
			.update();
		assertThat(copied).isEqualTo(1);
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
	static class FakePaymentConfig {

		@Bean
		@Primary
		FakePaymentGateway fakePaymentGateway() {
			return new FakePaymentGateway();
		}

	}

}
