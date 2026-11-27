package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 订单已提交的集成事件，是销售上下文对外发布的稳定契约。
 *
 * <p>刻意不复用领域模型 {@code OrderLine}：跨上下文契约不随领域实现变化。
 */
public record OrderPlacedIntegrationEvent(UUID eventId, Instant occurredAt, OrderId orderId, CustomerId customerId,
		Currency currency, Money total, Money discount, Money payable, List<OrderLinePayload> lines)
		implements IntegrationEvent {

	public static final String EVENT_TYPE = "sales.order.placed.v1";

	public static final String AGGREGATE_TYPE = "sales.order";

	public OrderPlacedIntegrationEvent {
		lines = List.copyOf(lines);
	}

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
		return orderId.value().toString();
	}

}

/**
 * 集成事件中的订单行载荷。
 */
record OrderLinePayload(Sku sku, String productName, Money unitPrice, int quantity) {
}
