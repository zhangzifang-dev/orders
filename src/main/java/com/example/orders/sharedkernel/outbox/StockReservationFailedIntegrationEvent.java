package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 库存预占被拒绝的集成事件，携带面向使用方的中文原因。
 */
public record StockReservationFailedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId,
		String reason) implements IntegrationEvent {

	public static final String EVENT_TYPE = "inventory.stock.reservation-failed.v1";

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
