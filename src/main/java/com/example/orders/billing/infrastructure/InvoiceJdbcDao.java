package com.example.orders.billing.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

/**
 * {@link InvoiceRecord} 的 Spring Data JDBC 仓储。
 */
interface InvoiceJdbcDao extends CrudRepository<InvoiceRecord, UUID> {

	Optional<InvoiceRecord> findByOrderId(UUID orderId);

}
