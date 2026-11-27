package com.example.orders.billing.domain;

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

class InvoiceTest {

	private static final Instant ISSUED_AT = Instant.parse("2026-02-01T10:15:30Z");

	private static final Instant LATER = Instant.parse("2026-02-02T08:00:00Z");

	@Test
	void issuedInvoiceStartsIssuedAndRegistersEvent() {
		InvoiceId invoiceId = InvoiceId.newId();
		OrderId orderId = OrderId.newId();
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		Money amount = Money.of("120.00", Currency.CNY);

		Invoice invoice = Invoice.issue(invoiceId, orderId, customerId, amount, ISSUED_AT);

		assertThat(invoice.invoiceId()).isEqualTo(invoiceId);
		assertThat(invoice.orderId()).isEqualTo(orderId);
		assertThat(invoice.customerId()).isEqualTo(customerId);
		assertThat(invoice.amount()).isEqualTo(amount);
		assertThat(invoice.status()).isEqualTo(InvoiceStatus.ISSUED);
		assertThat(invoice.issuedAt()).isEqualTo(ISSUED_AT);
		List<DomainEvent> events = invoice.pullDomainEvents();
		assertThat(events).singleElement().isInstanceOf(InvoiceIssued.class);
		assertThat(events.get(0).occurredAt()).isEqualTo(ISSUED_AT);
	}

	@Test
	void invoiceAmountMustBePositive() {
		Money zero = Money.of("0.00", Currency.CNY);
		Money negative = Money.of("-0.01", Currency.CNY);

		assertThatThrownBy(() -> Invoice.issue(InvoiceId.newId(), OrderId.newId(), CustomerId.of(UUID.randomUUID()),
				zero, ISSUED_AT))
			.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Invoice.issue(InvoiceId.newId(), OrderId.newId(), CustomerId.of(UUID.randomUUID()),
				negative, ISSUED_AT))
			.isInstanceOf(DomainException.class);
	}

	@Test
	void paidInvoiceCannotBeVoided() {
		Invoice invoice = newInvoice();
		invoice.markPaid(LATER);

		assertThatThrownBy(() -> invoice.voidIt(LATER)).isInstanceOf(InvalidPaymentStateException.class);

		assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAID);
	}

	@Test
	void voidedInvoiceCannotBePaid() {
		Invoice invoice = newInvoice();
		invoice.voidIt(LATER);

		assertThatThrownBy(() -> invoice.markPaid(LATER)).isInstanceOf(InvalidPaymentStateException.class);

		assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOIDED);
	}

	@Test
	void invoiceEventsCarryPassedInstant() {
		Invoice invoice = newInvoice();
		assertThat(invoice.pullDomainEvents()).extracting(DomainEvent::occurredAt).containsExactly(ISSUED_AT);

		invoice.voidIt(LATER);

		List<DomainEvent> events = invoice.pullDomainEvents();
		assertThat(events).singleElement().isInstanceOf(InvoiceVoided.class);
		assertThat(events.get(0).occurredAt()).isEqualTo(LATER);
	}

	private static Invoice newInvoice() {
		return Invoice.issue(InvoiceId.newId(), OrderId.newId(), CustomerId.of(UUID.randomUUID()),
				Money.of("120.00", Currency.CNY), ISSUED_AT);
	}

}
