package com.example.orders.sharedkernel;

/**
 * 订单数量，取值范围 1..999。
 */
public record Quantity(int value) {

	public Quantity {
		if (value < 1 || value > 999) {
			throw new DomainException("数量必须在 1 到 999 之间: " + value);
		}
	}

	public static Quantity of(int value) {
		return new Quantity(value);
	}

}
