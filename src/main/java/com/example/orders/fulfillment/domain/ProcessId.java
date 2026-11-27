package com.example.orders.fulfillment.domain;

import java.util.UUID;

import com.example.orders.sharedkernel.DomainException;

/**
 * 履约流程标识。
 */
public record ProcessId(UUID value) {

	public ProcessId {
		if (value == null) {
			throw new DomainException("流程 ID 不能为空");
		}
	}

	public static ProcessId newId() {
		return new ProcessId(UUID.randomUUID());
	}

	public static ProcessId of(UUID value) {
		return new ProcessId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
