package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 发票已开具的集成事件：计费上下文把 {@code InvoiceIssued} 领域事件翻译后的稳定契约。
 *
 * <p>发票 ID 以原始 {@link UUID} 表达，避免共享内核反向依赖计费上下文的值对象。
 */
public record InvoiceIssuedIntegrationEvent(UUID eventId, Instant occurredAt, UUID invoiceId, OrderId orderId,
		CustomerId customerId, Money amount) implements IntegrationEvent {

	public static final String EVENT_TYPE = "billing.invoice.issued.v1";

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
