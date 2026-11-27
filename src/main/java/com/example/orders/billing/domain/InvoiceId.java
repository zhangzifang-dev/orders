package com.example.orders.billing.domain;

import java.util.UUID;

import com.example.orders.sharedkernel.DomainException;

/**
 * 发票标识。
 */
public record InvoiceId(UUID value) {

	public InvoiceId {
		if (value == null) {
			throw new DomainException("发票 ID 不能为空");
		}
	}

	public static InvoiceId newId() {
		return new InvoiceId(UUID.randomUUID());
	}

	public static InvoiceId of(UUID value) {
		return new InvoiceId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
