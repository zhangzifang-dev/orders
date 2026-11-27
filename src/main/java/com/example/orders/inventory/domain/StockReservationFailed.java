package com.example.orders.inventory.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 预占被拒绝时产生的领域事件。
 *
 * <p>由应用层在捕获 {@link InsufficientStockException} 后构造；聚合本身不产生该事件。
 */
public record StockReservationFailed(UUID eventId, Instant occurredAt, OrderId orderId, Sku sku, String reason)
		implements DomainEvent {

	@Override
	public String eventType() {
		return "inventory.stock.reservation.failed";
	}

	@Override
	public String aggregateId() {
		return sku.value();
	}

}
