package com.example.orders.billing.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 发票被开具时产生的领域事件。
 */
public record InvoiceIssued(UUID eventId, Instant occurredAt, InvoiceId invoiceId, OrderId orderId,
		CustomerId customerId, Money amount) implements DomainEvent {

	@Override
	public String eventType() {
		return "billing.invoice.issued";
	}

	@Override
	public String aggregateId() {
		return invoiceId.toString();
	}

}
