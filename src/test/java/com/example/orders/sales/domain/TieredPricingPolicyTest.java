package com.example.orders.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

class TieredPricingPolicyTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

	private final PricingPolicy policy = new TieredPricingPolicy();

	@Test
	void fewerThanTenItemsGetNoDiscount() {
		Order order = orderWith(9, "100.00");

		assertThat(policy.discountFor(order)).isEqualTo(Money.zero(Currency.CNY));
	}

	@Test
	void tenItemsGetFivePercentOff() {
		Order order = orderWith(10, "100.00");

		assertThat(policy.discountFor(order)).isEqualTo(Money.of("50.00", Currency.CNY));
	}

	@Test
	void twentyItemsGetTenPercentOff() {
		Order order = orderWith(20, "100.00");

		assertThat(policy.discountFor(order)).isEqualTo(Money.of("200.00", Currency.CNY));
	}

	@Test
	void tiersApplyToTotalQuantityAcrossLines() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of("100.00", Currency.CNY), Quantity.of(12),
				OCCURRED_AT);
		order.addLine(LineId.newId(), Sku.of("SKU-2"), "鼠标", Money.of("100.00", Currency.CNY), Quantity.of(8),
				OCCURRED_AT);

		assertThat(policy.discountFor(order)).isEqualTo(Money.of("200.00", Currency.CNY));
	}

	@Test
	void discountScaleIsTwoAndHalfUp() {
		Order order = orderWith(10, "10.05");

		assertThat(policy.discountFor(order)).isEqualTo(Money.of("5.03", Currency.CNY));
	}

	private static Order orderWith(int quantity, String unitPrice) {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of(unitPrice, Currency.CNY),
				Quantity.of(quantity), OCCURRED_AT);
		return order;
	}

}
