package com.example.orders.fulfillment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;

class FulfillmentProcessTest {

	private static final Instant START_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant RESERVED_AT = Instant.parse("2026-01-01T00:01:00Z");

	private static final Instant PAID_AT = Instant.parse("2026-01-01T00:02:00Z");

	private static final Instant COMPLETED_AT = Instant.parse("2026-01-01T00:03:00Z");

	private static final Instant COMPENSATING_AT = Instant.parse("2026-01-01T00:04:00Z");

	private static final Instant CANCELLED_AT = Instant.parse("2026-01-01T00:05:00Z");

	private static final Instant FAILED_AT = Instant.parse("2026-01-01T00:06:00Z");

	@Test
	void startCreatesStartedProcess() {
		OrderId orderId = OrderId.newId();

		FulfillmentProcess process = FulfillmentProcess.start(orderId, START_AT);

		assertThat(process.processId()).isNotNull();
		assertThat(process.orderId()).isEqualTo(orderId);
		assertThat(process.status()).isEqualTo(FulfillmentStatus.STARTED);
		assertThat(process.failureReason()).isNull();
		assertThat(process.updatedAt()).isEqualTo(START_AT);
	}

	@Test
	void happyPathTransitions() {
		FulfillmentProcess process = FulfillmentProcess.start(OrderId.newId(), START_AT);

		process.markStockReserved(RESERVED_AT);
		assertThat(process.status()).isEqualTo(FulfillmentStatus.STOCK_RESERVED);
		assertThat(process.updatedAt()).isEqualTo(RESERVED_AT);

		process.markPaid(PAID_AT);
		assertThat(process.status()).isEqualTo(FulfillmentStatus.PAID);
		assertThat(process.updatedAt()).isEqualTo(PAID_AT);

		process.complete(COMPLETED_AT);
		assertThat(process.status()).isEqualTo(FulfillmentStatus.COMPLETED);
		assertThat(process.updatedAt()).isEqualTo(COMPLETED_AT);
		assertThat(process.failureReason()).isNull();
	}

	@Test
	void cannotSkipSteps() {
		FulfillmentProcess started = FulfillmentProcess.start(OrderId.newId(), START_AT);
		assertThatThrownBy(() -> started.markPaid(PAID_AT)).isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> started.complete(COMPLETED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThat(started.status()).isEqualTo(FulfillmentStatus.STARTED);

		FulfillmentProcess reserved = FulfillmentProcess.start(OrderId.newId(), START_AT);
		reserved.markStockReserved(RESERVED_AT);
		assertThatThrownBy(() -> reserved.complete(COMPLETED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThat(reserved.status()).isEqualTo(FulfillmentStatus.STOCK_RESERVED);

		FulfillmentProcess paid = FulfillmentProcess.start(OrderId.newId(), START_AT);
		paid.markStockReserved(RESERVED_AT);
		paid.markPaid(PAID_AT);
		assertThatThrownBy(() -> paid.markStockReserved(RESERVED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThat(paid.status()).isEqualTo(FulfillmentStatus.PAID);
	}

	@Test
	void compensationOnlyFromStockReservedOrPaid() {
		FulfillmentProcess started = FulfillmentProcess.start(OrderId.newId(), START_AT);
		assertThatThrownBy(() -> started.startCompensation("支付失败", COMPENSATING_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThat(started.status()).isEqualTo(FulfillmentStatus.STARTED);
		assertThat(started.failureReason()).isNull();

		FulfillmentProcess reserved = FulfillmentProcess.start(OrderId.newId(), START_AT);
		reserved.markStockReserved(RESERVED_AT);
		reserved.startCompensation("支付失败", COMPENSATING_AT);
		assertThat(reserved.status()).isEqualTo(FulfillmentStatus.COMPENSATING);
		assertThat(reserved.failureReason()).isEqualTo("支付失败");
		assertThat(reserved.updatedAt()).isEqualTo(COMPENSATING_AT);

		FulfillmentProcess paid = FulfillmentProcess.start(OrderId.newId(), START_AT);
		paid.markStockReserved(RESERVED_AT);
		paid.markPaid(PAID_AT);
		paid.startCompensation("下游超时", COMPENSATING_AT);
		assertThat(paid.status()).isEqualTo(FulfillmentStatus.COMPENSATING);
		assertThat(paid.failureReason()).isEqualTo("下游超时");
	}

	@Test
	void compensationKeepsReasonThroughCancellation() {
		FulfillmentProcess process = FulfillmentProcess.start(OrderId.newId(), START_AT);
		process.markStockReserved(RESERVED_AT);
		process.startCompensation("库存回滚", COMPENSATING_AT);

		process.markCancelled(CANCELLED_AT);

		assertThat(process.status()).isEqualTo(FulfillmentStatus.CANCELLED);
		assertThat(process.failureReason()).isEqualTo("库存回滚");
		assertThat(process.updatedAt()).isEqualTo(CANCELLED_AT);
	}

	@Test
	void cancellationOnlyFromCompensating() {
		FulfillmentProcess reserved = FulfillmentProcess.start(OrderId.newId(), START_AT);
		reserved.markStockReserved(RESERVED_AT);
		assertThatThrownBy(() -> reserved.markCancelled(CANCELLED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThat(reserved.status()).isEqualTo(FulfillmentStatus.STOCK_RESERVED);

		FulfillmentProcess started = FulfillmentProcess.start(OrderId.newId(), START_AT);
		assertThatThrownBy(() -> started.markCancelled(CANCELLED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThat(started.status()).isEqualTo(FulfillmentStatus.STARTED);
	}

	@Test
	void failedAllowedBeforeCompletionOnly() {
		FulfillmentProcess started = FulfillmentProcess.start(OrderId.newId(), START_AT);
		started.markFailed("库存不足", FAILED_AT);
		assertThat(started.status()).isEqualTo(FulfillmentStatus.FAILED);
		assertThat(started.failureReason()).isEqualTo("库存不足");

		FulfillmentProcess reserved = FulfillmentProcess.start(OrderId.newId(), START_AT);
		reserved.markStockReserved(RESERVED_AT);
		reserved.markFailed("库存释放失败", FAILED_AT);
		assertThat(reserved.status()).isEqualTo(FulfillmentStatus.FAILED);
		assertThat(reserved.failureReason()).isEqualTo("库存释放失败");

		FulfillmentProcess paid = FulfillmentProcess.start(OrderId.newId(), START_AT);
		paid.markStockReserved(RESERVED_AT);
		paid.markPaid(PAID_AT);
		paid.markFailed("退款失败", FAILED_AT);
		assertThat(paid.status()).isEqualTo(FulfillmentStatus.FAILED);
		assertThat(paid.failureReason()).isEqualTo("退款失败");
	}

	@Test
	void terminalStatesRejectFurtherTransitions() {
		FulfillmentProcess completed = completedProcess();
		assertAllTransitionsRejected(completed, FulfillmentStatus.COMPLETED, null);

		FulfillmentProcess cancelled = FulfillmentProcess.start(OrderId.newId(), START_AT);
		cancelled.markStockReserved(RESERVED_AT);
		cancelled.startCompensation("支付失败", COMPENSATING_AT);
		cancelled.markCancelled(CANCELLED_AT);
		assertAllTransitionsRejected(cancelled, FulfillmentStatus.CANCELLED, "支付失败");

		FulfillmentProcess failed = FulfillmentProcess.start(OrderId.newId(), START_AT);
		failed.markFailed("库存不足", FAILED_AT);
		assertAllTransitionsRejected(failed, FulfillmentStatus.FAILED, "库存不足");
	}

	@Test
	void blankReasonsAreRejectedWithoutChangingState() {
		FulfillmentProcess reserved = FulfillmentProcess.start(OrderId.newId(), START_AT);
		reserved.markStockReserved(RESERVED_AT);

		assertThatThrownBy(() -> reserved.startCompensation("   ", COMPENSATING_AT))
			.isInstanceOf(DomainException.class);
		assertThat(reserved.status()).isEqualTo(FulfillmentStatus.STOCK_RESERVED);
		assertThat(reserved.failureReason()).isNull();

		FulfillmentProcess started = FulfillmentProcess.start(OrderId.newId(), START_AT);
		assertThatThrownBy(() -> started.markFailed(null, FAILED_AT)).isInstanceOf(DomainException.class);
		assertThat(started.status()).isEqualTo(FulfillmentStatus.STARTED);
		assertThat(started.failureReason()).isNull();
	}

	@Test
	void illegalTransitionsLeaveStateAndReasonUntouched() {
		FulfillmentProcess process = FulfillmentProcess.start(OrderId.newId(), START_AT);
		process.markStockReserved(RESERVED_AT);
		process.startCompensation("原始原因", COMPENSATING_AT);
		Instant before = process.updatedAt();

		assertThatThrownBy(() -> process.markPaid(PAID_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.markStockReserved(RESERVED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.complete(COMPLETED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.markFailed("新原因", FAILED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.startCompensation("新原因", COMPENSATING_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);

		assertThat(process.status()).isEqualTo(FulfillmentStatus.COMPENSATING);
		assertThat(process.failureReason()).isEqualTo("原始原因");
		assertThat(process.updatedAt()).isEqualTo(before);
	}

	@Test
	void processIdSupportsNewIdAndOf() {
		UUID value = UUID.randomUUID();

		assertThat(ProcessId.of(value).value()).isEqualTo(value);
		assertThat(ProcessId.newId()).isNotEqualTo(ProcessId.newId());
		assertThatThrownBy(() -> ProcessId.of(null)).isInstanceOf(DomainException.class);
	}

	private static FulfillmentProcess completedProcess() {
		FulfillmentProcess process = FulfillmentProcess.start(OrderId.newId(), START_AT);
		process.markStockReserved(RESERVED_AT);
		process.markPaid(PAID_AT);
		process.complete(COMPLETED_AT);
		return process;
	}

	private static void assertAllTransitionsRejected(FulfillmentProcess process, FulfillmentStatus expectedStatus,
			String expectedReason) {
		Instant before = process.updatedAt();
		assertThatThrownBy(() -> process.markStockReserved(RESERVED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.markPaid(PAID_AT)).isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.complete(COMPLETED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.startCompensation("原因", COMPENSATING_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.markCancelled(CANCELLED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);
		assertThatThrownBy(() -> process.markFailed("原因", FAILED_AT))
			.isInstanceOf(InvalidFulfillmentStateException.class);

		assertThat(process.status()).isEqualTo(expectedStatus);
		assertThat(process.failureReason()).isEqualTo(expectedReason);
		assertThat(process.updatedAt()).isEqualTo(before);
	}

}
