package com.example.orders.billing.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;

/**
 * 发票被作废时产生的领域事件。
 */
public record InvoiceVoided(UUID eventId, Instant occurredAt, InvoiceId invoiceId) implements DomainEvent {

	@Override
	public String eventType() {
		return "billing.invoice.voided";
	}

	@Override
	public String aggregateId() {
		return invoiceId.toString();
	}

}
