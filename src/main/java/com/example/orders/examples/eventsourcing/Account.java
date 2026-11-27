package com.example.orders.examples.eventsourcing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

/**
 * 账户聚合根：状态由事件重放得到，命令方法只产生新的事件。
 *
 * <p>{@link #version()} 表示已应用的事件数量；{@link #uncommittedEvents()} 返回当前实例
 * 产生但尚未追加到 {@link EventStore} 的事件。注意新开立账户的 {@code version() == 1}，
 * 但首次追加时 {@link EventStore#append} 的 {@code expectedVersion} 应为 0，因为
 * {@code expectedVersion} 表示追加前的事件流长度。
 */
public class Account {

	private final AccountId accountId;

	private final List<AccountEvent> uncommittedEvents = new ArrayList<>();

	private Money balance;

	private long version;

	private Account(AccountId accountId, Money balance) {
		if (accountId == null) {
			throw new DomainException("账户 ID 不能为空");
		}
		if (balance == null) {
			throw new DomainException("余额不能为空");
		}
		this.accountId = accountId;
		this.balance = balance;
	}

	/**
	 * 开立账户并产生 {@link AccountOpened} 事件。
	 * @param accountId 账户 ID
	 * @param initialBalance 初始余额
	 * @param occurredAt 事件发生时间
	 * @return 新账户
	 */
	public static Account open(AccountId accountId, Money initialBalance, Instant occurredAt) {
		Account account = new Account(accountId, initialBalance);
		account.record(new AccountOpened(accountId, initialBalance, occurredAt));
		return account;
	}

	/**
	 * 从事件流重建账户：事件流不能为空，且必须以 {@link AccountOpened} 开始。
	 * @param events 按发生顺序排列的事件
	 * @return 重建后的账户，不携带未提交事件
	 */
	public static Account rehydrate(List<AccountEvent> events) {
		if (events == null || events.isEmpty()) {
			throw new DomainException("事件流不能为空");
		}
		AccountEvent first = events.get(0);
		if (!(first instanceof AccountOpened opened)) {
			throw new DomainException("事件流必须以 AccountOpened 开始");
		}

		Account account = new Account(opened.accountId(), opened.initialBalance());
		for (int i = 0; i < events.size(); i++) {
			AccountEvent event = events.get(i);
			if (event == null) {
				throw new DomainException("事件不能为空");
			}
			if (i > 0 && event instanceof AccountOpened) {
				throw new DomainException("事件流中不能出现重复的 AccountOpened");
			}
			account.apply(event);
		}
		return account;
	}

	/**
	 * 存款并产生 {@link MoneyDeposited} 事件。
	 * @param amount 存款金额
	 * @param occurredAt 事件发生时间
	 */
	public void deposit(Money amount, Instant occurredAt) {
		record(new MoneyDeposited(amount, occurredAt));
	}

	/**
	 * 取款并产生 {@link MoneyWithdrawn} 事件；余额不足时拒绝且不产生事件。
	 * @param amount 取款金额
	 * @param occurredAt 事件发生时间
	 */
	public void withdraw(Money amount, Instant occurredAt) {
		if (amount == null) {
			throw new DomainException("取款金额不能为空");
		}
		if (amount.isGreaterThan(balance)) {
			throw new DomainException("余额不足: balance=" + balance + ", requested=" + amount);
		}
		record(new MoneyWithdrawn(amount, occurredAt));
	}

	public Money balance() {
		return balance;
	}

	public long version() {
		return version;
	}

	public List<AccountEvent> uncommittedEvents() {
		return List.copyOf(uncommittedEvents);
	}

	private void record(AccountEvent event) {
		apply(event);
		uncommittedEvents.add(event);
	}

	private void apply(AccountEvent event) {
		switch (event) {
			case AccountOpened opened -> this.balance = opened.initialBalance();
			case MoneyDeposited deposited -> this.balance = this.balance.add(deposited.amount());
			case MoneyWithdrawn withdrawn -> this.balance = this.balance.subtract(withdrawn.amount());
		}
		this.version++;
	}

}
