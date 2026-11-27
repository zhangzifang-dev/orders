package com.example.orders.billing.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.example.orders.billing.domain.Invoice;
import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.InvoiceRepository;
import com.example.orders.billing.domain.InvoiceStatus;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Repository;

/**
 * 基于 Spring Data JDBC 的 {@link InvoiceRepository} 实现，手写领域对象与行记录之间的映射。
 */
@Repository
public class JdbcInvoiceRepository implements InvoiceRepository {

	private final InvoiceJdbcDao dao;

	public JdbcInvoiceRepository(InvoiceJdbcDao dao) {
		this.dao = dao;
	}

	@Override
	public void save(Invoice invoice) {
		UUID id = invoice.invoiceId().value();
		Long version = dao.existsById(id) ? Long.valueOf(invoice.version()) : null;
		InvoiceRecord saved = dao.save(toRecord(invoice, version));
		invoice.assignVersion(saved.version());
	}

	@Override
	public Optional<Invoice> findById(InvoiceId invoiceId) {
		return dao.findById(invoiceId.value()).map(this::toDomain);
	}

	@Override
	public Optional<Invoice> findByOrderId(OrderId orderId) {
		return dao.findByOrderId(orderId.value()).map(this::toDomain);
	}

	private InvoiceRecord toRecord(Invoice invoice, Long version) {
		return new InvoiceRecord(invoice.invoiceId().value(), invoice.orderId().value(), invoice.customerId().value(),
				invoice.amount().amount(), invoice.amount().currency().code(), invoice.status().name(),
				invoice.issuedAt(), version);
	}

	private Invoice toDomain(InvoiceRecord record) {
		Currency currency = Currency.of(record.currency());
		return Invoice.rehydrate(InvoiceId.of(record.id()), OrderId.of(record.orderId()),
				CustomerId.of(record.customerId()), Money.of(record.amount(), currency),
				InvoiceStatus.valueOf(record.status()), record.issuedAt(), record.version());
	}

}
