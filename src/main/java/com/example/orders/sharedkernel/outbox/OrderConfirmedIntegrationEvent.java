package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 订单已确认的集成事件，供履约等下游上下文消费。
 */
public record OrderConfirmedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId)
		implements IntegrationEvent {

	public static final String EVENT_TYPE = "sales.order.confirmed.v1";

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
