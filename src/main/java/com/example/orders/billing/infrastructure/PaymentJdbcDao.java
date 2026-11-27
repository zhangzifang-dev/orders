package com.example.orders.billing.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

/**
 * {@link PaymentRecord} 的 Spring Data JDBC 仓储。
 */
interface PaymentJdbcDao extends CrudRepository<PaymentRecord, UUID> {

	Optional<PaymentRecord> findByInvoiceId(UUID invoiceId);

}
