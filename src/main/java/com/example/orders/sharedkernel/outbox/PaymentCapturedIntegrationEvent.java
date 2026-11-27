package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 支付已捕获的集成事件：计费上下文对外发布的稳定契约。
 *
 * <p>发票与支付以原始 {@link UUID} 表达，避免共享内核反向依赖计费上下文的值对象。
 */
public record PaymentCapturedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId, UUID invoiceId,
		UUID paymentId, Money amount) implements IntegrationEvent {

	public static final String EVENT_TYPE = "billing.payment.captured.v1";

	public static final String AGGREGATE_TYPE = "billing.payment";

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
		return paymentId.toString();
	}

}
