package com.example.orders.fulfillment.domain;

import java.time.Instant;

import com.example.orders.sharedkernel.AggregateRoot;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;

/**
 * 履约流程聚合根：以显式状态机驱动库存预占、支付、完成与补偿。
 *
 * <p>合法迁移：
 * <ul>
 * <li>{@code STARTED -> STOCK_RESERVED}</li>
 * <li>{@code STOCK_RESERVED -> PAID}</li>
 * <li>{@code PAID -> COMPLETED}</li>
 * <li>{@code STOCK_RESERVED | PAID -> COMPENSATING}</li>
 * <li>{@code COMPENSATING -> CANCELLED}</li>
 * <li>{@code STARTED | STOCK_RESERVED | PAID -> FAILED}</li>
 * </ul>
 * 其他迁移均被拒绝；{@code COMPLETED}、{@code CANCELLED}、{@code FAILED} 为终态。
 *
 * <p>时间由调用方显式传入，聚合内不读取系统时钟，也不注册领域事件。
 */
public class FulfillmentProcess extends AggregateRoot {

	private final ProcessId processId;

	private final OrderId orderId;

	private FulfillmentStatus status;

	private String failureReason;

	private Instant updatedAt;

	private FulfillmentProcess(ProcessId processId, OrderId orderId, Instant startedAt) {
		if (processId == null) {
			throw new DomainException("流程 ID 不能为空");
		}
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		if (startedAt == null) {
			throw new DomainException("启动时间不能为空");
		}
		this.processId = processId;
		this.orderId = orderId;
		this.status = FulfillmentStatus.STARTED;
		this.updatedAt = startedAt;
	}

	public static FulfillmentProcess start(OrderId orderId, Instant startedAt) {
		return new FulfillmentProcess(ProcessId.newId(), orderId, startedAt);
	}

	/**
	 * {@code STARTED -> STOCK_RESERVED}。
	 * @param occurredAt 迁移时间
	 */
	public void markStockReserved(Instant occurredAt) {
		requireInstant(occurredAt);
		switch (status) {
			case STARTED -> {
				this.status = FulfillmentStatus.STOCK_RESERVED;
				this.updatedAt = occurredAt;
			}
			default -> throw invalidTransition("预占库存");
		}
	}

	/**
	 * {@code STOCK_RESERVED -> PAID}。
	 * @param occurredAt 迁移时间
	 */
	public void markPaid(Instant occurredAt) {
		requireInstant(occurredAt);
		switch (status) {
			case STOCK_RESERVED -> {
				this.status = FulfillmentStatus.PAID;
				this.updatedAt = occurredAt;
			}
			default -> throw invalidTransition("标记已支付");
		}
	}

	/**
	 * {@code PAID -> COMPLETED}。
	 * @param occurredAt 迁移时间
	 */
	public void complete(Instant occurredAt) {
		requireInstant(occurredAt);
		switch (status) {
			case PAID -> {
				this.status = FulfillmentStatus.COMPLETED;
				this.updatedAt = occurredAt;
			}
			default -> throw invalidTransition("完成履约");
		}
	}

	/**
	 * {@code STOCK_RESERVED | PAID -> COMPENSATING}。
	 * @param reason 补偿原因，不能为空白
	 * @param occurredAt 迁移时间
	 */
	public void startCompensation(String reason, Instant occurredAt) {
		requireReason(reason);
		requireInstant(occurredAt);
		switch (status) {
			case STOCK_RESERVED, PAID -> {
				this.status = FulfillmentStatus.COMPENSATING;
				this.failureReason = reason;
				this.updatedAt = occurredAt;
			}
			default -> throw invalidTransition("开始补偿");
		}
	}

	/**
	 * {@code COMPENSATING -> CANCELLED}，保留补偿原因。
	 * @param occurredAt 迁移时间
	 */
	public void markCancelled(Instant occurredAt) {
		requireInstant(occurredAt);
		switch (status) {
			case COMPENSATING -> {
				this.status = FulfillmentStatus.CANCELLED;
				this.updatedAt = occurredAt;
			}
			default -> throw invalidTransition("取消流程");
		}
	}

	/**
	 * {@code STARTED | STOCK_RESERVED | PAID -> FAILED}。
	 * @param reason 失败原因，不能为空白
	 * @param occurredAt 迁移时间
	 */
	public void markFailed(String reason, Instant occurredAt) {
		requireReason(reason);
		requireInstant(occurredAt);
		switch (status) {
			case STARTED, STOCK_RESERVED, PAID -> {
				this.status = FulfillmentStatus.FAILED;
				this.failureReason = reason;
				this.updatedAt = occurredAt;
			}
			default -> throw invalidTransition("标记失败");
		}
	}

	private InvalidFulfillmentStateException invalidTransition(String action) {
		return new InvalidFulfillmentStateException(
				"当前状态 " + status + " 不允许执行「" + action + "」");
	}

	private static void requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new DomainException("原因不能为空");
		}
	}

	private static void requireInstant(Instant occurredAt) {
		if (occurredAt == null) {
			throw new DomainException("迁移时间不能为空");
		}
	}

	public ProcessId processId() {
		return processId;
	}

	public OrderId orderId() {
		return orderId;
	}

	public FulfillmentStatus status() {
		return status;
	}

	public String failureReason() {
		return failureReason;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

}
