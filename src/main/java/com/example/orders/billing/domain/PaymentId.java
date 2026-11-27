package com.example.orders.billing.domain;

import java.util.UUID;

import com.example.orders.sharedkernel.DomainException;

/**
 * 支付标识。
 */
public record PaymentId(UUID value) {

	public PaymentId {
		if (value == null) {
			throw new DomainException("支付 ID 不能为空");
		}
	}

	public static PaymentId newId() {
		return new PaymentId(UUID.randomUUID());
	}

	public static PaymentId of(UUID value) {
		return new PaymentId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
