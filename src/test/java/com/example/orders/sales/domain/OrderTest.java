package com.example.orders.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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

class OrderTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void totalIsSumOfLineSubtotals() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(2),
				OCCURRED_AT);
		order.addLine(LineId.newId(), Sku.of("SKU-2"), "鼠标", Money.of("99.00", Currency.CNY), Quantity.of(1),
				OCCURRED_AT);
		assertThat(order.total()).isEqualTo(Money.of("497.00", Currency.CNY));
	}

	@Test
	void rejectsLineWithDifferentCurrency() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);

		assertThatThrownBy(() -> order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘",
				Money.of("199.00", Currency.of("USD")), Quantity.of(2), OCCURRED_AT))
			.isInstanceOf(DomainException.class);

		assertThat(order.lines()).isEmpty();
	}

	@Test
	void removeLineUpdatesTotal() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		LineId kept = LineId.newId();
		LineId removed = LineId.newId();
		order.addLine(kept, Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(2), OCCURRED_AT);
		order.addLine(removed, Sku.of("SKU-2"), "鼠标", Money.of("99.00", Currency.CNY), Quantity.of(1),
				OCCURRED_AT);

		order.removeLine(removed, OCCURRED_AT);

		assertThat(order.total()).isEqualTo(Money.of("398.00", Currency.CNY));
		assertThat(order.lines()).extracting(OrderLine::lineId).containsExactly(kept);
	}

	@Test
	void emptyDraftHasZeroTotalAndPayable() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);

		assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
		assertThat(order.total()).isEqualTo(Money.zero(Currency.CNY));
		assertThat(order.payable()).isEqualTo(Money.zero(Currency.CNY));
	}

	@Test
	void payableEqualsTotalBeforeDiscounting() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(2),
				OCCURRED_AT);

		assertThat(order.payable()).isEqualTo(order.total());
	}

	@Test
	void rejectsRemovingUnknownLine() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);

		assertThatThrownBy(() -> order.removeLine(LineId.newId(), OCCURRED_AT))
			.isInstanceOf(DomainException.class);
	}

	@Test
	void lineEventsUseProvidedInstant() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		LineId lineId = LineId.newId();

		order.addLine(lineId, Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(2),
				OCCURRED_AT);
		order.removeLine(lineId, OCCURRED_AT);

		List<DomainEvent> events = order.pullDomainEvents();
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(OrderLineAdded.class);
		assertThat(events.get(1)).isInstanceOf(OrderLineRemoved.class);
		assertThat(events).allSatisfy(event -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT));
	}

}
