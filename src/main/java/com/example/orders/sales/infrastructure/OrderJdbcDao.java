package com.example.orders.sales.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

/**
 * {@link OrderRecord} 的 Spring Data JDBC 仓储。
 */
interface OrderJdbcDao extends CrudRepository<OrderRecord, UUID> {

	List<OrderRecord> findByCustomerId(UUID customerId);

}
