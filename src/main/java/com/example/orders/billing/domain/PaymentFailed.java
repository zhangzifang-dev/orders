package com.example.orders.billing.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.Money;

/**
 * 支付失败时产生的领域事件。
 */
public record PaymentFailed(UUID eventId, Instant occurredAt, PaymentId paymentId, InvoiceId invoiceId,
		Money amount, String failureReason) implements DomainEvent {

	@Override
	public String eventType() {
		return "billing.payment.failed";
	}

	@Override
	public String aggregateId() {
		return paymentId.toString();
	}

}
