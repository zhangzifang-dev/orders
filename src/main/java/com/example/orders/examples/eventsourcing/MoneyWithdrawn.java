package com.example.orders.examples.eventsourcing;

import java.time.Instant;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

/**
 * 取款事件。
 */
public record MoneyWithdrawn(Money amount, Instant occurredAt) implements AccountEvent {

	public MoneyWithdrawn {
		if (amount == null) {
			throw new DomainException("取款金额不能为空");
		}
		if (amount.isNegative()) {
			throw new DomainException("取款金额不能为负数");
		}
		if (occurredAt == null) {
			throw new DomainException("事件发生时间不能为空");
		}
	}

}
