package com.example.orders.billing.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.Money;

/**
 * 支付被捕获时产生的领域事件。
 */
public record PaymentCaptured(UUID eventId, Instant occurredAt, PaymentId paymentId, InvoiceId invoiceId,
		Money amount, String gatewayRef) implements DomainEvent {

	@Override
	public String eventType() {
		return "billing.payment.captured";
	}

	@Override
	public String aggregateId() {
		return paymentId.toString();
	}

}
