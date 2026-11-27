package com.example.orders.inventory.infrastructure;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code inventory_stock_item} 表的行记录，仅用于持久化，不承载领域行为。
 */
@Table("inventory_stock_item")
public record StockItemRecord(@Id String sku, String productName, int onHand, @Version Long version,
		@MappedCollection(idColumn = "sku") Set<ReservationRecord> reservations) {

	public StockItemRecord {
		reservations = reservations == null ? Set.of() : new LinkedHashSet<>(reservations);
	}

}
