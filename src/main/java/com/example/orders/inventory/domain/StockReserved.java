package com.example.orders.inventory.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 库存被某订单成功预占时产生的领域事件。
 */
public record StockReserved(UUID eventId, Instant occurredAt, OrderId orderId, Sku sku, int quantity)
		implements DomainEvent {

	@Override
	public String eventType() {
		return "inventory.stock.reserved";
	}

	@Override
	public String aggregateId() {
		return sku.value();
	}

}
