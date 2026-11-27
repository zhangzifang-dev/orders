package com.example.orders.billing.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code billing_payment} 表的行记录，仅用于持久化，不承载领域行为。
 */
@Table("billing_payment")
public record PaymentRecord(@Id UUID id, UUID invoiceId, BigDecimal amount, String currency, String status,
		String gatewayRef, String failureReason, Instant createdAt, @Version Long version) {
}
