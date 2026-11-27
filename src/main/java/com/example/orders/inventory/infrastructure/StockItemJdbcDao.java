package com.example.orders.inventory.infrastructure;

import org.springframework.data.repository.CrudRepository;

/**
 * {@link StockItemRecord} 的 Spring Data JDBC 仓储。
 */
interface StockItemJdbcDao extends CrudRepository<StockItemRecord, String> {
}
