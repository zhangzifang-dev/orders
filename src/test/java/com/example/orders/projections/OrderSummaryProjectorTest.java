package com.example.orders.projections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sales.application.CreateOrderCommand;
import com.example.orders.sales.application.CreateOrderLineCommand;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * CQRS 订单摘要读模型的端到端测试：真实 Spring 上下文、真实 H2 与 outbox relay，不使用 Mockito。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderSummaryProjectorTest {

	private static final String CONSUMER = OrderSummaryProjector.CONSUMER;

	@Autowired
	private OrderApplicationService orderApi;

	@Autowired
	private OrderSummaryQuery summaryQuery;

	@Autowired
	private OrderSummaryRebuilder rebuilder;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void resetProjection() {
		jdbcClient.sql("DELETE FROM order_summary_view").update();
		jdbcClient.sql("DELETE FROM processed_event WHERE consumer = :consumer").param("consumer", CONSUMER).update();
	}

	@Test
	void placedOrderAppearsInReadModel() {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		OrderId orderId = placeOrder(customerId, 2);

		relay.relayOnce();

		List<OrderSummary> summaries = summaryQuery.byCustomer(customerId);
		assertThat(summaries).hasSize(1);
		OrderSummary summary = summaries.getFirst();
		assertThat(summary.orderId()).isEqualTo(orderId);
		assertThat(summary.customerId()).isEqualTo(customerId);
		assertThat(summary.status()).isEqualTo(OrderSummaryStatus.PLACED);
		assertThat(summary.total()).isEqualTo(Money.of("398.00", Currency.CNY));
		assertThat(summary.lineCount()).isEqualTo(1);
		assertThat(summary.updatedAt()).isNotNull();
	}

	@Test
	void confirmUpdatesStatusInPlace() {
		Sku sku = newSku();
		givenStock(sku, 5);
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		OrderId orderId = placeOrder(customerId, sku, 1);

		// 第一轮发布 OrderPlaced，履约同步完成并追加 OrderConfirmed；第二轮把确认投影进读模型。
		relay.relayOnce();
		relay.relayOnce();

		List<OrderSummary> summaries = summaryQuery.byCustomer(customerId);
		assertThat(summaries).hasSize(1);
		assertThat(summaries.getFirst().orderId()).isEqualTo(orderId);
		assertThat(summaries.getFirst().status()).isEqualTo(OrderSummaryStatus.CONFIRMED);
		assertThat(rowCount(orderId)).isEqualTo(1);
	}

	@Test
	void cancelledOrderUpdatesStatusInPlace() {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		OrderId orderId = placeOrder(customerId, 1);

		// 无库存时履约在首轮发布后取消订单并追加 OrderCancelled；第二轮把取消投影进读模型。
		relay.relayOnce();
		relay.relayOnce();

		List<OrderSummary> summaries = summaryQuery.byCustomer(customerId);
		assertThat(summaries).hasSize(1);
		assertThat(summaries.getFirst().status()).isEqualTo(OrderSummaryStatus.CANCELLED);
		assertThat(rowCount(orderId)).isEqualTo(1);
	}

	@Test
	void duplicateEventDoesNotDuplicateRow() {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		OrderId orderId = placeOrder(customerId, 1);
		duplicatePlacedEvent(orderId);

		relay.relayOnce();

		assertThat(summaryQuery.byCustomer(customerId)).hasSize(1);
		assertThat(rowCount(orderId)).isEqualTo(1);
	}

	@Test
	void rebuildFromWriteModelRestoresView() {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		OrderId orderId = placeOrder(customerId, 1);

		jdbcClient.sql("DELETE FROM order_summary_view").update();
		assertThat(summaryQuery.byCustomer(customerId)).isEmpty();

		rebuilder.rebuild();

		List<OrderSummary> summaries = summaryQuery.byCustomer(customerId);
		assertThat(summaries).hasSize(1);
		assertThat(summaries.getFirst().orderId()).isEqualTo(orderId);
		assertThat(summaries.getFirst().status()).isEqualTo(OrderSummaryStatus.PLACED);
		assertThat(summaries.getFirst().lineCount()).isEqualTo(1);
		long placedOrderCount = orderApi.listAll().stream().filter(order -> order.placedAt() != null).count();
		assertThat(rowCount()).isEqualTo(placedOrderCount);
	}

	@Test
	void draftOrderIsNotProjectedByRebuild() {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		orderApi.create(new CreateOrderCommand(customerId, Currency.CNY,
				List.of(new CreateOrderLineCommand(newSku(), "机械键盘", Money.of("199.00", Currency.CNY),
						Quantity.of(1)))));

		jdbcClient.sql("DELETE FROM order_summary_view").update();
		rebuilder.rebuild();

		assertThat(summaryQuery.byCustomer(customerId)).isEmpty();
	}

	@Test
	void pendingPlacedEventAfterRebuildDoesNotStallRelay() {
		CustomerId rebuiltCustomer = CustomerId.of(UUID.randomUUID());
		OrderId rebuiltOrder = placeOrder(rebuiltCustomer, 1);
		rebuilder.rebuild();

		CustomerId laterCustomer = CustomerId.of(UUID.randomUUID());
		placeOrder(laterCustomer, 1);

		// 重建已插入 rebuiltOrder 的行，但还没有 processed_event 记录；待投递的 OrderPlaced 必须幂等。
		relay.relayOnce();

		assertThat(summaryQuery.byCustomer(rebuiltCustomer)).hasSize(1);
		assertThat(rowCount(rebuiltOrder)).isEqualTo(1);
		// 若重建后的重复 OrderPlaced 触发主键冲突，relay 会中断，后一个订单不会被投影。
		assertThat(summaryQuery.byCustomer(laterCustomer)).hasSize(1);
	}

	private OrderId placeOrder(CustomerId customerId, int quantity) {
		return placeOrder(customerId, newSku(), quantity);
	}

	private OrderId placeOrder(CustomerId customerId, Sku sku, int quantity) {
		OrderId orderId = orderApi.create(new CreateOrderCommand(customerId, Currency.CNY,
				List.of(new CreateOrderLineCommand(sku, "机械键盘", Money.of("199.00", Currency.CNY),
						Quantity.of(quantity)))));
		orderApi.place(orderId);
		return orderId;
	}

	private Sku newSku() {
		return Sku.of("SKU-" + UUID.randomUUID());
	}

	private void givenStock(Sku sku, int onHand) {
		stockItemRepository.save(StockItem.create(sku, "机械键盘", 0));
		inventoryService.restock(sku, onHand);
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

	private long rowCount(OrderId orderId) {
		return jdbcClient.sql("SELECT COUNT(*) FROM order_summary_view WHERE order_id = :orderId")
			.param("orderId", orderId.value())
			.query(Long.class)
			.single();
	}

	private long rowCount() {
		return jdbcClient.sql("SELECT COUNT(*) FROM order_summary_view").query(Long.class).single();
	}

}
