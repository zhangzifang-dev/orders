package com.example.orders.bootstrap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.example.orders.billing.infrastructure.SimulatedLegacyPaymentClient;
import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sales.application.CreateOrderCommand;
import com.example.orders.sales.application.CreateOrderLineCommand;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.OutboxRelay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code demo} profile 的可运行演示：依次执行成功路径与支付失败补偿路径，并打印事件时间线。
 *
 * <p>生产环境的 outbox relay 没有调度器；演示中由本类显式循环调用 {@link OutboxRelay#relayOnce()}
 * 直至没有待发布事件，因此结果完全确定。
 */
@Component
@Profile("demo")
public class DemoRunner implements ApplicationRunner {

	private static final String DECLINED_STATUS_CODE = "51";

	private static final String SUCCESS_STATUS_CODE = "00";

	private static final int MAX_RELAY_ROUNDS = 100;

	/**
	 * 按订单收集时间线。计费事件的 {@code aggregate_id} 是发票/支付 ID，因此除聚合 ID 外还需匹配
	 * payload 中的订单 ID，才能把 {@code billing.payment.*} 纳入该订单的时间线。
	 */
	private static final String TIMELINE_SQL = """
			SELECT occurred_at, event_type
			FROM outbox_event
			WHERE aggregate_id IN (:successOrderId, :failureOrderId)
			   OR CAST(payload AS VARCHAR) LIKE :successPattern
			   OR CAST(payload AS VARCHAR) LIKE :failurePattern
			ORDER BY occurred_at, id
			""";

	private final OrderApplicationService orderApplicationService;

	private final InventoryApplicationService inventoryApplicationService;

	private final StockItemRepository stockItemRepository;

	private final OutboxRelay outboxRelay;

	private final SimulatedLegacyPaymentClient paymentClient;

	private final JdbcClient jdbcClient;

	private final boolean runOnStartup;

	public DemoRunner(OrderApplicationService orderApplicationService,
			InventoryApplicationService inventoryApplicationService, StockItemRepository stockItemRepository,
			OutboxRelay outboxRelay, SimulatedLegacyPaymentClient paymentClient, JdbcClient jdbcClient,
			@Value("${demo.run-on-startup:true}") boolean runOnStartup) {
		this.orderApplicationService = orderApplicationService;
		this.inventoryApplicationService = inventoryApplicationService;
		this.stockItemRepository = stockItemRepository;
		this.outboxRelay = outboxRelay;
		this.paymentClient = paymentClient;
		this.jdbcClient = jdbcClient;
		this.runOnStartup = runOnStartup;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!runOnStartup) {
			return;
		}
		System.out.println(runScenarios());
	}

	/**
	 * 执行两个演示场景并返回完整事件时间线，供测试直接调用。
	 * @return 含两个订单 ID 与按发生时间排序的事件时间线的文本
	 */
	public String runScenarios() {
		ensureStock(DemoData.SUCCESS_SKU, DemoData.SUCCESS_PRODUCT);
		ensureStock(DemoData.FAILURE_SKU, DemoData.FAILURE_PRODUCT);

		OrderId successOrderId = placeOrder(DemoData.SUCCESS_SKU, DemoData.SUCCESS_PRODUCT, DemoData.SUCCESS_PRICE,
				DemoData.SUCCESS_QUANTITY);
		relayUntilIdle();

		OrderId failureOrderId = placeFailingOrder();
		relayUntilIdle();

		return renderTimeline(successOrderId, failureOrderId);
	}

	private void ensureStock(Sku sku, String productName) {
		if (stockItemRepository.findById(sku).isEmpty()) {
			stockItemRepository.save(StockItem.create(sku, productName, 0));
		}
		inventoryApplicationService.restock(sku, DemoData.RESTOCK_QUANTITY);
	}

	private OrderId placeFailingOrder() {
		paymentClient.setStatusCode(DECLINED_STATUS_CODE);
		try {
			OrderId orderId = placeOrder(DemoData.FAILURE_SKU, DemoData.FAILURE_PRODUCT, DemoData.FAILURE_PRICE,
					DemoData.FAILURE_QUANTITY);
			relayUntilIdle();
			return orderId;
		}
		finally {
			paymentClient.setStatusCode(SUCCESS_STATUS_CODE);
		}
	}

	private OrderId placeOrder(Sku sku, String productName, Money unitPrice, int quantity) {
		CustomerId customerId = DemoData.CUSTOMER_ID;
		OrderId orderId = orderApplicationService
			.create(new CreateOrderCommand(customerId, DemoData.CURRENCY,
					List.of(new CreateOrderLineCommand(sku, productName, unitPrice, Quantity.of(quantity)))));
		orderApplicationService.place(orderId);
		return orderId;
	}

	private void relayUntilIdle() {
		int rounds = 0;
		while (outboxRelay.relayOnce() > 0) {
			rounds++;
			if (rounds >= MAX_RELAY_ROUNDS) {
				throw new IllegalStateException("outbox relay 未在 " + MAX_RELAY_ROUNDS + " 轮内处理完待发布事件");
			}
		}
	}

	private String renderTimeline(OrderId successOrderId, OrderId failureOrderId) {
		String successId = successOrderId.value().toString();
		String failureId = failureOrderId.value().toString();
		List<TimelineEntry> entries = jdbcClient.sql(TIMELINE_SQL)
			.param("successOrderId", successId)
			.param("failureOrderId", failureId)
			.param("successPattern", "%" + successId + "%")
			.param("failurePattern", "%" + failureId + "%")
			.query(this::mapTimelineEntry)
			.list();

		String timeline = entries.stream()
			.map(entry -> "[" + entry.occurredAt() + "] " + entry.eventType())
			.collect(Collectors.joining("\n"));

		return """
				Demo: success path and payment-failure compensation
				success order: %s
				failure order: %s
				event timeline:
				%s
				""".formatted(successId, failureId, timeline);
	}

	private TimelineEntry mapTimelineEntry(ResultSet rs, int rowNumber) throws SQLException {
		Instant occurredAt = rs.getObject("occurred_at", OffsetDateTime.class).toInstant();
		return new TimelineEntry(occurredAt, rs.getString("event_type"));
	}

	private record TimelineEntry(Instant occurredAt, String eventType) {
	}

}
