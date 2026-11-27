package com.example.orders.inventory.infrastructure;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code inventory_reservation} 表的行记录，是库存项的子实体。
 */
@Table("inventory_reservation")
public record ReservationRecord(@Id UUID orderId, int quantity) {
}
