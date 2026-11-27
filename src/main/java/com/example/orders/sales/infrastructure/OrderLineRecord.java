package com.example.orders.sales.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * {@code sales_order_line} 表的行记录，仅用于持久化，不承载领域行为。
 */
@Table("sales_order_line")
public record OrderLineRecord(@Id UUID id, String sku, String productName, BigDecimal unitPriceAmount, int quantity) {
}
