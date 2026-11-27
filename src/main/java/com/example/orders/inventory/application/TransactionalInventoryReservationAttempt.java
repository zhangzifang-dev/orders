package com.example.orders.inventory.application;

import java.util.List;

import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InventoryReservationAttempt} 的默认实现：每次调用都在新事务中执行预占。
 */
@Service
public class TransactionalInventoryReservationAttempt implements InventoryReservationAttempt {

	private final InventoryApplicationService inventoryApplicationService;

	public TransactionalInventoryReservationAttempt(InventoryApplicationService inventoryApplicationService) {
		this.inventoryApplicationService = inventoryApplicationService;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ReservationResult reserve(OrderId orderId, List<ReserveLine> lines) {
		return inventoryApplicationService.reserve(orderId, lines);
	}

}
