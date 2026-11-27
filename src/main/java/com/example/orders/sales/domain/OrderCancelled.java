package com.example.orders.sales.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单被取消时产生的领域事件，携带取消原因。
 */
public record OrderCancelled(UUID eventId, Instant occurredAt, OrderId orderId, String reason)
		implements DomainEvent {

	@Override
	public String eventType() {
		return "sales.order.cancelled";
	}

	@Override
	public String aggregateId() {
		return orderId.toString();
	}

}
