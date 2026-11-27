package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 库存预占成功的集成事件：一个订单一次预占，携带全部 SKU 行。
 */
public record StockReservedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId,
		List<StockLinePayload> lines) implements IntegrationEvent {

	public static final String EVENT_TYPE = "inventory.stock.reserved.v1";

	public static final String AGGREGATE_TYPE = "inventory.stock";

	public StockReservedIntegrationEvent {
		lines = List.copyOf(lines);
	}

	@Override
	public String eventType() {
		return EVENT_TYPE;
	}

	@Override
	public String aggregateType() {
		return AGGREGATE_TYPE;
	}

	@Override
	public String aggregateId() {
		return orderId.value().toString();
	}

	/**
	 * 集成事件中的库存行载荷。作为公共嵌套类型，其他上下文可直接读取其访问器。
	 */
	public record StockLinePayload(Sku sku, int quantity) {
	}

}
