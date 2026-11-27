package com.example.orders.inventory.application;

import java.util.List;

import com.example.orders.sharedkernel.OrderId;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 预占库存的并发边界：在事务性尝试之外重试一次乐观锁冲突。
 *
 * <p>重试必须放在独立的非事务 Bean 中，否则同类自调用会绕过 Spring 事务代理。每次尝试由
 * {@link InventoryReservationAttempt} 在独立的新事务中执行，因此第一次冲突回滚不会污染重试。
 * 两次尝试后仍冲突时返回可重试的库存不足结果。
 */
@Service
public class InventoryReservationFacade {

	private static final String CONCURRENT_CONFLICT = "并发冲突，请重试";

	private final InventoryReservationAttempt attempt;

	public InventoryReservationFacade(InventoryReservationAttempt attempt) {
		this.attempt = attempt;
	}

	public ReservationResult reserve(OrderId orderId, List<ReserveLine> lines) {
		try {
			return attempt.reserve(orderId, lines);
		}
		catch (OptimisticLockingFailureException firstConflict) {
			try {
				return attempt.reserve(orderId, lines);
			}
			catch (OptimisticLockingFailureException retryConflict) {
				return new StockShortage(CONCURRENT_CONFLICT);
			}
		}
	}

}
