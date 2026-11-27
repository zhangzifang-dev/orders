package com.example.orders.sales.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单被确认时产生的领域事件。
 */
public record OrderConfirmed(UUID eventId, Instant occurredAt, OrderId orderId) implements DomainEvent {

	@Override
	public String eventType() {
		return "sales.order.confirmed";
	}

	@Override
	public String aggregateId() {
		return orderId.toString();
	}

}
