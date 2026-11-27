package com.example.orders.examples.eventsourcing;

import java.time.Instant;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

/**
 * 账户开立事件：账户事件流的第一个事件。
 */
public record AccountOpened(AccountId accountId, Money initialBalance, Instant occurredAt)
		implements AccountEvent {

	public AccountOpened {
		if (accountId == null) {
			throw new DomainException("账户 ID 不能为空");
		}
		if (initialBalance == null) {
			throw new DomainException("初始余额不能为空");
		}
		if (initialBalance.isNegative()) {
			throw new DomainException("初始余额不能为负数");
		}
		if (occurredAt == null) {
			throw new DomainException("事件发生时间不能为空");
		}
	}

}
