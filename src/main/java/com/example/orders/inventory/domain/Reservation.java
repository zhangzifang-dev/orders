package com.example.orders.inventory.domain;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;

/**
 * 某订单对某个 {@link StockItem} 的预占，是聚合内部的实体。
 */
public record Reservation(OrderId orderId, int quantity) {

	public Reservation {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		if (quantity <= 0) {
			throw new DomainException("预占数量必须大于 0: " + quantity);
		}
	}

}
