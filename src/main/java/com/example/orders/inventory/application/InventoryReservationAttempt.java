package com.example.orders.inventory.application;

import java.util.List;

import com.example.orders.sharedkernel.OrderId;

/**
 * 单次事务性的库存预占尝试。
 *
 * <p>从重试门面中抽出，使每次尝试都拥有独立事务：第一次乐观锁冲突回滚后，重试不会落在已标记
 * rollback-only 的事务里。
 */
public interface InventoryReservationAttempt {

	ReservationResult reserve(OrderId orderId, List<ReserveLine> lines);

}
