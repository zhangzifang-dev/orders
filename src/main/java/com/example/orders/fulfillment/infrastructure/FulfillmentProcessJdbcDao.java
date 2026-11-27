package com.example.orders.fulfillment.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

/**
 * {@link FulfillmentProcessRecord} 的 Spring Data JDBC 仓储。
 */
interface FulfillmentProcessJdbcDao extends CrudRepository<FulfillmentProcessRecord, UUID> {

	Optional<FulfillmentProcessRecord> findByOrderId(UUID orderId);

}
