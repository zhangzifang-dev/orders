package com.example.orders.sales.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

/**
 * 订单行被加入时产生的领域事件。
 */
public record OrderLineAdded(UUID eventId, Instant occurredAt, OrderId orderId, LineId lineId, Sku sku,
		String productName, Money unitPrice, Quantity quantity) implements DomainEvent {

	@Override
	public String eventType() {
		return "sales.order.line-added";
	}

	@Override
	public String aggregateId() {
		return orderId.toString();
	}

}
