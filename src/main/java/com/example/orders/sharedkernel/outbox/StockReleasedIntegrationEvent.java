package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 某订单的库存预占被释放的集成事件。
 */
public record StockReleasedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId)
		implements IntegrationEvent {

	public static final String EVENT_TYPE = "inventory.stock.released.v1";

	public static final String AGGREGATE_TYPE = "inventory.stock";

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

}
