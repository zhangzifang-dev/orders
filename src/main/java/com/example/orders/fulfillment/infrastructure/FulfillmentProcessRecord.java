package com.example.orders.fulfillment.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code fulfillment_process} 表的行记录，仅用于持久化，不承载领域行为。
 */
@Table("fulfillment_process")
public record FulfillmentProcessRecord(@Id UUID id, UUID orderId, String status, String failureReason,
		Instant updatedAt, @Version Long version) {
}
