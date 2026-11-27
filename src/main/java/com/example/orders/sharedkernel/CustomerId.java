package com.example.orders.sharedkernel;

import java.util.UUID;

/**
 * 客户标识。
 */
public record CustomerId(UUID value) {

	public CustomerId {
		if (value == null) {
			throw new DomainException("客户 ID 不能为空");
		}
	}

	public static CustomerId of(UUID value) {
		return new CustomerId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
