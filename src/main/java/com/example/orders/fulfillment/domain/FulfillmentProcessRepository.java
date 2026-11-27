package com.example.orders.fulfillment.domain;

import java.util.Optional;

import com.example.orders.sharedkernel.OrderId;

/**
 * 履约流程聚合的持久化端口。
 */
public interface FulfillmentProcessRepository {

	void save(FulfillmentProcess process);

	Optional<FulfillmentProcess> findById(ProcessId processId);

	Optional<FulfillmentProcess> findByOrderId(OrderId orderId);

}
