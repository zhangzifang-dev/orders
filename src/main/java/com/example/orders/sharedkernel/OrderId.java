package com.example.orders.sharedkernel;

import java.util.UUID;

/**
 * 订单标识。
 */
public record OrderId(UUID value) {

	public OrderId {
		if (value == null) {
			throw new DomainException("订单 ID 不能为空");
		}
	}

	public static OrderId newId() {
		return new OrderId(UUID.randomUUID());
	}

	public static OrderId of(UUID value) {
		return new OrderId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
