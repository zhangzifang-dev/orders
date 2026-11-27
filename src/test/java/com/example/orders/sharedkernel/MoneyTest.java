package com.example.orders.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void roundsToTwoDecimalsHalfUp() {
		assertThat(Money.of("10.005", Currency.CNY).amount()).isEqualByComparingTo("10.01");
	}

	@Test
	void rejectsCurrencyMismatch() {
		assertThatThrownBy(() -> Money.of("1.00", Currency.CNY).add(Money.of("1.00", Currency.of("USD"))))
			.isInstanceOf(DomainException.class);
	}

	@Test
	void subtractBelowZeroIsAllowedButReported() {
		Money result = Money.of("1.00", Currency.CNY).subtract(Money.of("2.00", Currency.CNY));
		assertThat(result.isNegative()).isTrue();
	}

	@Test
	void addsAmountsOfSameCurrency() {
		assertThat(Money.of("1.10", Currency.CNY).add(Money.of("2.20", Currency.CNY)))
			.isEqualTo(Money.of("3.30", Currency.CNY));
	}

	@Test
	void multipliesByIntegerQuantity() {
		assertThat(Money.of("19.99", Currency.CNY).multiply(3)).isEqualTo(Money.of("59.97", Currency.CNY));
	}

	@Test
	void multipliesByDecimalFactor() {
		assertThat(Money.of("100.00", Currency.CNY).multiply(new BigDecimal("0.05")))
			.isEqualTo(Money.of("5.00", Currency.CNY));
	}

	@Test
	void zeroIsZeroAndNotNegative() {
		Money zero = Money.zero(Currency.CNY);
		assertThat(zero.isZero()).isTrue();
		assertThat(zero.isNegative()).isFalse();
		assertThat(zero).isEqualTo(Money.of("0.00", Currency.CNY));
	}

	@Test
	void comparesAmountsOfSameCurrency() {
		Money two = Money.of("2.00", Currency.CNY);
		Money one = Money.of("1.00", Currency.CNY);

		assertThat(two).isGreaterThan(one);
		assertThat(two.isGreaterThan(one)).isTrue();
		assertThat(one.isGreaterThan(two)).isFalse();
	}

	@Test
	void rejectsNullAmountAndCurrency() {
		assertThatThrownBy(() -> Money.of((BigDecimal) null, Currency.CNY)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Money.of("1.00", null)).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsInvalidAmountText() {
		assertThatThrownBy(() -> Money.of("not-a-number", Currency.CNY)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Money.of("  ", Currency.CNY)).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsNullOperand() {
		assertThatThrownBy(() -> Money.of("1.00", Currency.CNY).add(null)).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsMalformedCurrencyCode() {
		assertThatThrownBy(() -> Currency.of("usd")).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Currency.of("US")).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Currency.of(null)).isInstanceOf(DomainException.class);
	}

}
