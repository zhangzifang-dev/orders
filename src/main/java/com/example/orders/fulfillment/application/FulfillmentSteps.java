package com.example.orders.fulfillment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.orders.fulfillment.application.port.BillingOutcome;
import com.example.orders.fulfillment.application.port.BillingPort;
import com.example.orders.fulfillment.application.port.Charged;
import com.example.orders.fulfillment.application.port.InventoryPort;
import com.example.orders.fulfillment.application.port.ReservationResult;
import com.example.orders.fulfillment.application.port.ReserveLine;
import com.example.orders.fulfillment.application.port.Reserved;
import com.example.orders.fulfillment.application.port.SalesPort;
import com.example.orders.fulfillment.domain.FulfillmentProcess;
import com.example.orders.fulfillment.domain.FulfillmentProcessRepository;
import com.example.orders.fulfillment.domain.ProcessId;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.NotFoundException;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.outbox.FulfillmentCompletedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.FulfillmentFailedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.ProcessedEventStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 履约 saga 的步骤执行器：每个方法在<strong>独立事务</strong>中提交，保证一步一事务。
 *
 * <p>该类必须是独立 Bean：若在 {@link FulfillmentApplicationService} 内自调用，
 * {@code REQUIRES_NEW} 事务代理会被绕过。步骤方法只调用端口，不直接依赖其他上下文。
 */
@Service
public class FulfillmentSteps {

	private static final String CONSUMER = "fulfillment";

	private final FulfillmentProcessRepository processRepository;

	private final ProcessedEventStore processedEventStore;

	private final InventoryPort inventoryPort;

	private final BillingPort billingPort;

	private final SalesPort salesPort;

	private final OutboxAppender outboxAppender;

	private final Clock clock;

	public FulfillmentSteps(FulfillmentProcessRepository processRepository, ProcessedEventStore processedEventStore,
			InventoryPort inventoryPort, BillingPort billingPort, SalesPort salesPort, OutboxAppender outboxAppender,
			Clock clock) {
		this.processRepository = processRepository;
		this.processedEventStore = processedEventStore;
		this.inventoryPort = inventoryPort;
		this.billingPort = billingPort;
		this.salesPort = salesPort;
		this.outboxAppender = outboxAppender;
		this.clock = clock;
	}

	/**
	 * 认领事件并启动（或复用）流程。幂等标记与流程创建在同一事务内提交。
	 * @param orderId 订单 ID
	 * @param eventId 触发事件的 ID
	 * @return 已处理或已存在流程以外的情形返回新流程；重复事件返回空
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<FulfillmentProcess> startOrClaim(OrderId orderId, UUID eventId) {
		if (!processedEventStore.tryMarkProcessed(eventId, CONSUMER)) {
			return Optional.empty();
		}
		return Optional.of(processRepository.findByOrderId(orderId).orElseGet(() -> {
			FulfillmentProcess process = FulfillmentProcess.start(orderId, clock.instant());
			processRepository.save(process);
			return process;
		}));
	}

	/**
	 * 预占库存；成功后在同一事务内把流程推进到 {@code STOCK_RESERVED}。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ReservationResult reserve(FulfillmentProcess process, List<ReserveLine> lines) {
		ReservationResult result = inventoryPort.reserve(process.orderId(), lines);
		if (result instanceof Reserved) {
			process.markStockReserved(clock.instant());
			processRepository.save(process);
		}
		return result;
	}

	/**
	 * 扣款；成功后在同一事务内把流程推进到 {@code PAID}。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public BillingOutcome pay(FulfillmentProcess process, OrderId orderId, CustomerId customerId, Money amount) {
		BillingOutcome outcome = billingPort.issueAndCapture(orderId, customerId, amount);
		if (outcome instanceof Charged) {
			process.markPaid(clock.instant());
			processRepository.save(process);
		}
		return outcome;
	}

	/**
	 * 确认订单并把流程推进到 {@code COMPLETED}，同时追加完成事件。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void confirmAndComplete(FulfillmentProcess process, OrderId orderId) {
		salesPort.confirm(orderId);
		Instant now = clock.instant();
		process.complete(now);
		processRepository.save(process);
		outboxAppender.append(new FulfillmentCompletedIntegrationEvent(UUID.randomUUID(), now, orderId));
	}

	/**
	 * 补偿第一步：把流程推进到 {@code COMPENSATING}，为后续动作留下可自愈的中间状态。
	 *
	 * <p>幂等：已处于 {@code COMPENSATING} 或 {@code CANCELLED} 时直接返回；
	 * 其他状态（含终态）不做迁移，避免重放触发非法状态。
	 * @param processId 流程 ID
	 * @param reason 补偿原因
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompensating(ProcessId processId, String reason) {
		FulfillmentProcess process = load(processId);
		switch (process.status()) {
			case STOCK_RESERVED, PAID -> {
				process.startCompensation(reason, clock.instant());
				processRepository.save(process);
			}
			default -> {
				// COMPENSATING/CANCELLED 幂等跳过；COMPLETED/FAILED/STARTED 不在补偿范围。
			}
		}
	}

	/**
	 * 补偿第二步：释放库存。库存释放对同一订单天然幂等。
	 * @param orderId 订单 ID
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void releaseStock(OrderId orderId) {
		inventoryPort.release(orderId);
	}

	/**
	 * 补偿第三步：取消订单。适配器对已取消订单幂等跳过。
	 * @param orderId 订单 ID
	 * @param reason 取消原因
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void cancelOrder(OrderId orderId, String reason) {
		salesPort.cancel(orderId, reason);
	}

	/**
	 * 补偿收尾：把流程落到 {@code CANCELLED} 并追加失败事件。
	 *
	 * <p>自行读取最新状态，因此即使 {@link #markCompensating} 失败也能自愈：
	 * {@code STOCK_RESERVED}/{@code PAID} 时先开始补偿再取消，{@code COMPENSATING} 时直接取消；
	 * 终态与 {@code STARTED} 不做处理。次要失败原因会合并进事件原因。
	 * @param processId 流程 ID
	 * @param reason 主失败原因
	 * @param secondaryErrors 各补偿动作的次要失败，可为空
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void finalizeCancelled(ProcessId processId, String reason, List<String> secondaryErrors) {
		FulfillmentProcess process = load(processId);
		Instant now = clock.instant();
		switch (process.status()) {
			case STOCK_RESERVED, PAID -> process.startCompensation(reason, now);
			case COMPENSATING -> {
				// 已进入补偿，直接收尾。
			}
			default -> {
				return;
			}
		}
		process.markCancelled(now);
		processRepository.save(process);
		outboxAppender.append(
				new FulfillmentFailedIntegrationEvent(UUID.randomUUID(), now, process.orderId(),
						combinedReason(reason, secondaryErrors)));
	}

	private FulfillmentProcess load(ProcessId processId) {
		return processRepository.findById(processId)
			.orElseThrow(() -> new NotFoundException("履约流程不存在: " + processId));
	}

	private static String combinedReason(String reason, List<String> secondaryErrors) {
		if (secondaryErrors == null || secondaryErrors.isEmpty()) {
			return reason;
		}
		return reason + "（补偿异常: " + String.join("; ", secondaryErrors) + "）";
	}

	/**
	 * 标记流程失败并取消订单，同时追加失败事件。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failAndCancel(FulfillmentProcess process, OrderId orderId, String reason) {
		Instant now = clock.instant();
		process.markFailed(reason, now);
		processRepository.save(process);
		salesPort.cancel(orderId, reason);
		outboxAppender.append(new FulfillmentFailedIntegrationEvent(UUID.randomUUID(), now, orderId, reason));
	}

}
