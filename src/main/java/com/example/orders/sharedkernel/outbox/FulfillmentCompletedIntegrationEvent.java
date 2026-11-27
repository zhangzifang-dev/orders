package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 履约完成的集成事件。
 */
public record FulfillmentCompletedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId)
		implements IntegrationEvent {

	public static final String EVENT_TYPE = "fulfillment.completed.v1";

	public static final String AGGREGATE_TYPE = "fulfillment.process";

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
