package com.example.orders.sales.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code sales_order} 表的行记录，仅用于持久化，不承载领域行为。
 */
@Table("sales_order")
public record OrderRecord(@Id UUID id, UUID customerId, String status, String currency, BigDecimal discountAmount,
		Instant placedAt, String cancelReason, @Version Long version,
		@MappedCollection(idColumn = "order_id") Set<OrderLineRecord> lines) {

	public OrderRecord {
		lines = lines == null ? Set.of() : new LinkedHashSet<>(lines);
	}

}
