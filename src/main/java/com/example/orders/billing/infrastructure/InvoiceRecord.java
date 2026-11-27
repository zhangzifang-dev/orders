package com.example.orders.billing.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code billing_invoice} 表的行记录，仅用于持久化，不承载领域行为。
 */
@Table("billing_invoice")
public record InvoiceRecord(@Id UUID id, UUID orderId, UUID customerId, BigDecimal amount, String currency,
		String status, Instant issuedAt, @Version Long version) {
}
