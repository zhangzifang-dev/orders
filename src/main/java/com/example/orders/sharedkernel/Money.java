package com.example.orders.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 不可变金额值对象：{@code scale = 2}，舍入模式 {@link RoundingMode#HALF_UP}。
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

	public Money {
		if (amount == null) {
			throw new DomainException("金额不能为空");
		}
		if (currency == null) {
			throw new DomainException("币种不能为空");
		}
		amount = amount.setScale(2, RoundingMode.HALF_UP);
	}

	public static Money of(String amount, Currency currency) {
		if (amount == null || amount.isBlank()) {
			throw new DomainException("金额不能为空");
		}
		try {
			return new Money(new BigDecimal(amount.trim()), currency);
		}
		catch (NumberFormatException ex) {
			throw new DomainException("金额格式非法: " + amount, ex);
		}
	}

	public static Money of(BigDecimal amount, Currency currency) {
		return new Money(amount, currency);
	}

	public static Money zero(Currency currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money add(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money subtract(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	public Money multiply(int factor) {
		return multiply(BigDecimal.valueOf(factor));
	}

	public Money multiply(BigDecimal factor) {
		if (factor == null) {
			throw new DomainException("乘数不能为空");
		}
		return new Money(amount.multiply(factor), currency);
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isGreaterThan(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount) > 0;
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount);
	}

	private void requireSameCurrency(Money other) {
		if (other == null) {
			throw new DomainException("金额不能为空");
		}
		if (!currency.equals(other.currency)) {
			throw new DomainException("币种不一致: " + currency + " vs " + other.currency);
		}
	}

}
