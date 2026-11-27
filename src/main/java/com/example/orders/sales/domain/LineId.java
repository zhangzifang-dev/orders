package com.example.orders.sales.domain;

import java.util.UUID;

import com.example.orders.sharedkernel.DomainException;

/**
 * 订单行标识。
 */
public record LineId(UUID value) {

	public LineId {
		if (value == null) {
			throw new DomainException("订单行 ID 不能为空");
		}
	}

	public static LineId newId() {
		return new LineId(UUID.randomUUID());
	}

	public static LineId of(UUID value) {
		return new LineId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
