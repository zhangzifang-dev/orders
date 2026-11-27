package com.example.orders.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

class OrderStateTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void placeRequiresAtLeastOneLine() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);

		assertThatThrownBy(() -> order.place(new TieredPricingPolicy(), OCCURRED_AT))
			.isInstanceOf(DomainException.class);

		assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
	}

	@Test
	void placeMovesDraftToPlacedAndAppliesPricing() {
		Order order = orderWith(10, "100.00");

		order.place(new TieredPricingPolicy(), OCCURRED_AT);

		assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
		assertThat(order.isDraft()).isFalse();
		assertThat(order.discount()).isEqualTo(Money.of("50.00", Currency.CNY));
		assertThat(order.payable()).isEqualTo(Money.of("950.00", Currency.CNY));
		assertThat(order.placedAt()).isEqualTo(OCCURRED_AT);
	}

	@Test
	void placeFromNonDraftIsRejected() {
		Order order = orderWith(1, "100.00");
		order.place(new TieredPricingPolicy(), OCCURRED_AT);

		assertThatThrownBy(() -> order.place(new TieredPricingPolicy(), OCCURRED_AT))
			.isInstanceOf(InvalidOrderStateException.class);
	}

	@Test
	void placedOrderRejectsLineChanges() {
		Order order = orderWith(1, "100.00");
		order.place(new TieredPricingPolicy(), OCCURRED_AT);

		assertThatThrownBy(() -> order.addLine(LineId.newId(), Sku.of("SKU-X"), "显示器",
				Money.of("10.00", Currency.CNY), Quantity.of(1), OCCURRED_AT))
			.isInstanceOf(InvalidOrderStateException.class);
		assertThatThrownBy(() -> order.removeLine(order.lines().get(0).lineId(), OCCURRED_AT))
			.isInstanceOf(InvalidOrderStateException.class);
	}

	@Test
	void confirmOnlyFromPlaced() {
		Order draft = orderWith(1, "100.00");
		assertThatThrownBy(() -> draft.confirm(OCCURRED_AT)).isInstanceOf(InvalidOrderStateException.class);

		Order placed = orderWith(1, "100.00");
		placed.place(new TieredPricingPolicy(), OCCURRED_AT);
		placed.confirm(OCCURRED_AT);

		assertThat(placed.status()).isEqualTo(OrderStatus.CONFIRMED);
	}

	@Test
	void cancelRequiresNonBlankReason() {
		Order order = orderWith(1, "100.00");

		assertThatThrownBy(() -> order.cancel("  ", OCCURRED_AT)).isInstanceOf(DomainException.class);

		assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
	}

	@Test
	void cancelFromDraftSucceeds() {
		Order order = orderWith(1, "100.00");

		order.cancel("客户取消", OCCURRED_AT);

		assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(order.cancelReason()).isEqualTo("客户取消");
	}

	@Test
	void cancelFromPlacedSucceeds() {
		Order order = orderWith(1, "100.00");
		order.place(new TieredPricingPolicy(), OCCURRED_AT);

		order.cancel("缺货", OCCURRED_AT);

		assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void confirmedOrderCannotBeCancelled() {
		Order order = orderWith(1, "100.00");
		order.place(new TieredPricingPolicy(), OCCURRED_AT);
		order.confirm(OCCURRED_AT);

		assertThatThrownBy(() -> order.cancel("客户取消", OCCURRED_AT))
			.isInstanceOf(InvalidOrderStateException.class);

		assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
	}

	@Test
	void lifecycleEventsUseProvidedInstant() {
		Order order = orderWith(1, "100.00");
		order.place(new TieredPricingPolicy(), OCCURRED_AT);
		order.confirm(OCCURRED_AT);
		order.pullDomainEvents();

		Order cancelled = orderWith(1, "100.00");
		cancelled.pullDomainEvents();
		cancelled.place(new TieredPricingPolicy(), OCCURRED_AT);
		cancelled.pullDomainEvents();
		cancelled.cancel("客户取消", OCCURRED_AT);

		Order placed = orderWith(1, "100.00");
		placed.pullDomainEvents();
		placed.place(new TieredPricingPolicy(), OCCURRED_AT);

		Order confirmed = orderWith(1, "100.00");
		confirmed.pullDomainEvents();
		confirmed.place(new TieredPricingPolicy(), OCCURRED_AT);
		confirmed.pullDomainEvents();
		confirmed.confirm(OCCURRED_AT);

		assertThat(placed.pullDomainEvents()).singleElement()
			.isInstanceOfSatisfying(OrderPlaced.class, event -> {
				assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
				assertThat(event.aggregateId()).isEqualTo(placed.orderId().toString());
				assertThat(event.eventType()).isEqualTo("sales.order.placed");
			});
		assertThat(confirmed.pullDomainEvents()).singleElement()
			.isInstanceOfSatisfying(OrderConfirmed.class,
					event -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT));
		assertThat(cancelled.pullDomainEvents()).singleElement()
			.isInstanceOfSatisfying(OrderCancelled.class, event -> {
				assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
				assertThat(event.reason()).isEqualTo("客户取消");
			});
	}

	private static Order orderWith(int quantity, String unitPrice) {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of(unitPrice, Currency.CNY),
				Quantity.of(quantity), OCCURRED_AT);
		return order;
	}

}
