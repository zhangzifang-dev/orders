package com.example.orders.billing.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.Payment;
import com.example.orders.billing.domain.PaymentId;
import com.example.orders.billing.domain.PaymentRepository;
import com.example.orders.billing.domain.PaymentStatus;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.Money;

import org.springframework.stereotype.Repository;

/**
 * 基于 Spring Data JDBC 的 {@link PaymentRepository} 实现，手写领域对象与行记录之间的映射。
 */
@Repository
public class JdbcPaymentRepository implements PaymentRepository {

	private final PaymentJdbcDao dao;

	public JdbcPaymentRepository(PaymentJdbcDao dao) {
		this.dao = dao;
	}

	@Override
	public void save(Payment payment) {
		UUID id = payment.paymentId().value();
		Long version = dao.existsById(id) ? Long.valueOf(payment.version()) : null;
		PaymentRecord saved = dao.save(toRecord(payment, version));
		payment.assignVersion(saved.version());
	}

	@Override
	public Optional<Payment> findByInvoiceId(InvoiceId invoiceId) {
		return dao.findByInvoiceId(invoiceId.value()).map(this::toDomain);
	}

	private PaymentRecord toRecord(Payment payment, Long version) {
		return new PaymentRecord(payment.paymentId().value(), payment.invoiceId().value(), payment.amount().amount(),
				payment.amount().currency().code(), payment.status().name(), payment.gatewayRef(),
				payment.failureReason(), payment.createdAt(), version);
	}

	private Payment toDomain(PaymentRecord record) {
		Currency currency = Currency.of(record.currency());
		return Payment.rehydrate(PaymentId.of(record.id()), InvoiceId.of(record.invoiceId()),
				Money.of(record.amount(), currency), PaymentStatus.valueOf(record.status()), record.gatewayRef(),
				record.failureReason(), record.createdAt(), record.version());
	}

}
