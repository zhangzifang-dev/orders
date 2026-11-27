package com.example.orders.examples.eventsourcing;

import java.time.Instant;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

/**
 * 存款事件。
 */
public record MoneyDeposited(Money amount, Instant occurredAt) implements AccountEvent {

	public MoneyDeposited {
		if (amount == null) {
			throw new DomainException("存款金额不能为空");
		}
		if (amount.isNegative()) {
			throw new DomainException("存款金额不能为负数");
		}
		if (occurredAt == null) {
			throw new DomainException("事件发生时间不能为空");
		}
	}

}
