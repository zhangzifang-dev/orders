package com.example.orders.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

class PaymentTest {

	private static final Instant CREATED_AT = Instant.parse("2026-03-01T09:00:00Z");

	private static final Instant LATER = Instant.parse("2026-03-01T09:05:00Z");

	@Test
	void requestedPaymentStartsPending() {
		PaymentId paymentId = PaymentId.newId();
		InvoiceId invoiceId = InvoiceId.newId();
		Money amount = Money.of("120.00", Currency.CNY);

		Payment payment = Payment.request(paymentId, invoiceId, amount, CREATED_AT);

		assertThat(payment.paymentId()).isEqualTo(paymentId);
		assertThat(payment.invoiceId()).isEqualTo(invoiceId);
		assertThat(payment.amount()).isEqualTo(amount);
		assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(payment.gatewayRef()).isNull();
		assertThat(payment.failureReason()).isNull();
		assertThat(payment.createdAt()).isEqualTo(CREATED_AT);
		assertThat(payment.pullDomainEvents()).isEmpty();
	}

	@Test
	void paymentAmountMustBePositive() {
		assertThatThrownBy(() -> Payment.request(PaymentId.newId(), InvoiceId.newId(),
				Money.of("0.00", Currency.CNY), CREATED_AT))
			.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> Payment.request(PaymentId.newId(), InvoiceId.newId(),
				Money.of("-1.00", Currency.CNY), CREATED_AT))
			.isInstanceOf(DomainException.class);
	}

	@Test
	void captureMovesPendingToCapturedAndRegistersEventWithPassedInstant() {
		Payment payment = newPayment();

		payment.capture("gw-123", LATER);

		assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
		assertThat(payment.gatewayRef()).isEqualTo("gw-123");
		List<DomainEvent> events = payment.pullDomainEvents();
		assertThat(events).singleElement().isInstanceOf(PaymentCaptured.class);
		assertThat(events.get(0).occurredAt()).isEqualTo(LATER);
	}

	@Test
	void failMovesPendingToFailedAndRegistersEventWithPassedInstant() {
		Payment payment = newPayment();

		payment.fail("card declined", LATER);

		assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.failureReason()).isEqualTo("card declined");
		List<DomainEvent> events = payment.pullDomainEvents();
		assertThat(events).singleElement().isInstanceOf(PaymentFailed.class);
		assertThat(events.get(0).occurredAt()).isEqualTo(LATER);
	}

	@Test
	void capturedPaymentCannotFail() {
		Payment payment = newPayment();
		payment.capture("gw-123", LATER);

		assertThatThrownBy(() -> payment.fail("too late", LATER)).isInstanceOf(InvalidPaymentStateException.class);

		assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
		assertThat(payment.failureReason()).isNull();
	}

	@Test
	void failedPaymentCannotCapture() {
		Payment payment = newPayment();
		payment.fail("card declined", LATER);

		assertThatThrownBy(() -> payment.capture("gw-123", LATER)).isInstanceOf(InvalidPaymentStateException.class);

		assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.gatewayRef()).isNull();
	}

	private static Payment newPayment() {
		return Payment.request(PaymentId.newId(), InvoiceId.newId(), Money.of("120.00", Currency.CNY), CREATED_AT);
	}

}
