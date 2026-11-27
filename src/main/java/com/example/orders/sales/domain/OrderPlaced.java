package com.example.orders.sales.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单被提交（下单）时产生的领域事件，携带下单瞬间的金额快照与行项目。
 */
public record OrderPlaced(UUID eventId, Instant occurredAt, OrderId orderId, CustomerId customerId,
		Currency currency, Money total, Money discount, Money payable, List<OrderLine> lines) implements DomainEvent {

	public OrderPlaced {
		lines = List.copyOf(lines);
	}

	@Override
	public String eventType() {
		return "sales.order.placed";
	}

	@Override
	public String aggregateId() {
		return orderId.toString();
	}

}
