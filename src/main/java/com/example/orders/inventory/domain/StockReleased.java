package com.example.orders.inventory.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 某订单的库存预占被释放时产生的领域事件。
 */
public record StockReleased(UUID eventId, Instant occurredAt, OrderId orderId, Sku sku, int quantity)
		implements DomainEvent {

	@Override
	public String eventType() {
		return "inventory.stock.released";
	}

	@Override
	public String aggregateId() {
		return sku.value();
	}

}
