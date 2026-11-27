package com.example.orders.examples.eventsourcing;

import java.util.UUID;

import com.example.orders.sharedkernel.DomainException;

/**
 * 账户标识：事件溯源示例中的聚合根 ID。
 */
public record AccountId(UUID value) {

	public AccountId {
		if (value == null) {
			throw new DomainException("账户 ID 不能为空");
		}
	}

	public static AccountId newId() {
		return new AccountId(UUID.randomUUID());
	}

	public static AccountId of(UUID value) {
		return new AccountId(value);
	}

	public static AccountId of(String value) {
		if (value == null || value.isBlank()) {
			throw new DomainException("账户 ID 不能为空");
		}
		try {
			return new AccountId(UUID.fromString(value.trim()));
		}
		catch (IllegalArgumentException ex) {
			throw new DomainException("账户 ID 格式非法: " + value, ex);
		}
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
