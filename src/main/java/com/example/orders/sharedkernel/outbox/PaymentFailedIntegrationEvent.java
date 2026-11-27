package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.OrderId;

/**
 * 支付失败的集成事件：计费上下文对外发布的稳定契约。
 *
 * <p>发票与支付以原始 {@link UUID} 表达，避免共享内核反向依赖计费上下文的值对象。
 * 超时也以失败发布，原因固定为 {@code TIMEOUT}；真实系统需要通过查询网关或对账来消解超时的不确定性。
 */
public record PaymentFailedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId, UUID invoiceId,
		String reason) implements IntegrationEvent {

	public static final String EVENT_TYPE = "billing.payment.failed.v1";

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
