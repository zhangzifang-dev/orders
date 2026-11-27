package com.example.orders.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import org.junit.jupiter.api.Test;

import org.springframework.dao.OptimisticLockingFailureException;

/**
 * 重试门面的单元测试：使用手写 stub，确认第一次乐观锁冲突后会在新尝试中重试。
 */
class InventoryReservationFacadeTest {

	@Test
	void retriesOnceAfterOptimisticLockConflict() {
		StubAttempt attempt = new StubAttempt();
		InventoryReservationFacade facade = new InventoryReservationFacade(attempt);
		OrderId orderId = OrderId.newId();
		List<ReserveLine> lines = List.of(new ReserveLine(Sku.of("SKU-1"), 2));

		ReservationResult result = facade.reserve(orderId, lines);

		assertThat(result).isInstanceOf(Reserved.class);
		assertThat(attempt.calls).isEqualTo(2);
	}

	static final class StubAttempt implements InventoryReservationAttempt {

		private int calls;

		@Override
		public ReservationResult reserve(OrderId orderId, List<ReserveLine> lines) {
			calls++;
			if (calls == 1) {
				throw new OptimisticLockingFailureException("并发冲突");
			}
			return new Reserved(orderId, lines);
		}

	}

}
