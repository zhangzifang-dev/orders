package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 发票已作废的集成事件：计费上下文把 {@code InvoiceVoided} 领域事件翻译后的稳定契约。
 */
public record InvoiceVoidedIntegrationEvent(UUID eventId, Instant occurredAt, UUID invoiceId, OrderId orderId)
		implements IntegrationEvent {

	public static final String EVENT_TYPE = "billing.invoice.voided.v1";

	public static final String AGGREGATE_TYPE = "billing.invoice";

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
		return invoiceId.toString();
	}

}
