package com.example.orders.sales.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单行被移除时产生的领域事件。
 */
public record OrderLineRemoved(UUID eventId, Instant occurredAt, OrderId orderId, LineId lineId)
		implements DomainEvent {

	@Override
	public String eventType() {
		return "sales.order.line-removed";
	}

	@Override
	public String aggregateId() {
		return orderId.toString();
	}

}
