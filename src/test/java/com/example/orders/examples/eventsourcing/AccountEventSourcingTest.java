package com.example.orders.examples.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

/**
 * 独立事件溯源示例测试：不启动 Spring，不使用 mock。
 */
class AccountEventSourcingTest {

	private static final Currency CNY = Currency.CNY;

	private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");

	private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");

	@Test
	void rehydratesFromEvents() {
		AccountId accountId = AccountId.newId();
		InMemoryEventStore store = new InMemoryEventStore();

		store.append(accountId.toString(), List.of(
				new AccountOpened(accountId, Money.of("100.00", CNY), T1),
				new MoneyDeposited(Money.of("50.00", CNY), T2),
				new MoneyWithdrawn(Money.of("30.00", CNY), T3)), 0);

		Account account = Account.rehydrate(store.read(accountId.toString()));

		assertThat(account.balance()).isEqualTo(Money.of("120.00", CNY));
		assertThat(account.version()).isEqualTo(3);
		assertThat(account.uncommittedEvents()).isEmpty();
	}

	@Test
	void withdrawBeyondBalanceThrows() {
		Account account = Account.open(AccountId.newId(), Money.of("50.00", CNY), T1);

		assertThatThrownBy(() -> account.withdraw(Money.of("50.01", CNY), T2))
			.isInstanceOf(DomainException.class);

		assertThat(account.balance()).isEqualTo(Money.of("50.00", CNY));
		assertThat(account.version()).isEqualTo(1);
		assertThat(account.uncommittedEvents()).hasSize(1);
		assertThat(account.uncommittedEvents().get(0)).isInstanceOf(AccountOpened.class);
	}

	@Test
	void optimisticConcurrencyOnStreamVersion() {
		InMemoryEventStore store = new InMemoryEventStore();
		AccountId accountId = AccountId.newId();
		String streamId = accountId.toString();

		store.append(streamId,
				List.of(new AccountOpened(accountId, Money.of("100.00", CNY), T1)), 0);

		assertThatThrownBy(() -> store.append(streamId,
				List.of(new MoneyDeposited(Money.of("10.00", CNY), T2)), 0))
			.isInstanceOf(DomainException.class);

		store.append(streamId, List.of(new MoneyDeposited(Money.of("10.00", CNY), T2)), 1);

		assertThat(store.read(streamId)).hasSize(2);
	}

	@Test
	void rejectsEmptyEventStreamOnRehydrate() {
		assertThatThrownBy(() -> Account.rehydrate(List.of())).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsEventStreamNotStartingWithAccountOpened() {
		List<AccountEvent> events = List.of(new MoneyDeposited(Money.of("10.00", CNY), T1));

		assertThatThrownBy(() -> Account.rehydrate(events)).isInstanceOf(DomainException.class);
	}

	@Test
	void depositIncreasesBalanceAndRecordsEvent() {
		Account account = Account.open(AccountId.newId(), Money.of("100.00", CNY), T1);

		account.deposit(Money.of("25.00", CNY), T2);

		assertThat(account.balance()).isEqualTo(Money.of("125.00", CNY));
		assertThat(account.version()).isEqualTo(2);
		assertThat(account.uncommittedEvents()).hasSize(2);
		assertThat(account.uncommittedEvents().get(1)).isInstanceOf(MoneyDeposited.class);
	}

}
