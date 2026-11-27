package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 订单已取消的集成事件，携带取消原因。
 */
public record OrderCancelledIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId, String reason)
		implements IntegrationEvent {

	public static final String EVENT_TYPE = "sales.order.cancelled.v1";

	public static final String AGGREGATE_TYPE = "sales.order";

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
