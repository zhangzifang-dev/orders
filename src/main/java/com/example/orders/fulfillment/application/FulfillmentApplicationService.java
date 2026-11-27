package com.example.orders.fulfillment.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.orders.fulfillment.application.port.BillingOutcome;
import com.example.orders.fulfillment.application.port.ChargeFailed;
import com.example.orders.fulfillment.application.port.ReservationResult;
import com.example.orders.fulfillment.application.port.ReserveLine;
import com.example.orders.fulfillment.application.port.StockShortage;
import com.example.orders.fulfillment.domain.FulfillmentProcess;
import com.example.orders.fulfillment.domain.FulfillmentProcessRepository;
import com.example.orders.fulfillment.domain.FulfillmentStatus;
import com.example.orders.fulfillment.domain.ProcessId;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 履约 saga 的编排者：监听 {@code OrderPlaced}，按步骤调用 {@link FulfillmentSteps}。
 *
 * <p>本类<strong>不声明事务</strong>：outbox relay 已在自己的事务中分发事件，而每个 saga 步骤
 * 由 {@link FulfillmentSteps} 的 {@code REQUIRES_NEW} 事务独立提交。
 */
@Service
public class FulfillmentApplicationService {

	private static final Logger log = LoggerFactory.getLogger(FulfillmentApplicationService.class);

	private final FulfillmentSteps steps;

	private final FulfillmentProcessRepository processRepository;

	public FulfillmentApplicationService(FulfillmentSteps steps, FulfillmentProcessRepository processRepository) {
		this.steps = steps;
		this.processRepository = processRepository;
	}

	/**
	 * 订单提交后的成功路径：启动流程 → 预占库存 → 扣款 → 确认订单 → 完成。
	 * @param event 订单已提交集成事件
	 */
	@EventListener
	public void onOrderPlaced(OrderPlacedIntegrationEvent event) {
		Optional<FulfillmentProcess> claimed = steps.startOrClaim(event.orderId(), event.eventId());
		if (claimed.isEmpty()) {
			return;
		}
		FulfillmentProcess process = claimed.get();

		try {
			List<ReserveLine> lines = event.lines()
				.stream()
				.map(line -> new ReserveLine(line.sku(), line.quantity()))
				.toList();
			ReservationResult reservation = steps.reserve(process, lines);
			if (reservation instanceof StockShortage shortage) {
				steps.failAndCancel(process, event.orderId(), shortage.reason());
				return;
			}

			BillingOutcome billing = steps.pay(process, event.orderId(), event.customerId(), event.payable());
			if (billing instanceof ChargeFailed failed) {
				// 支付失败（含超时）：走非事务补偿编排，逐步释放库存、取消订单并收尾。
				compensate(process.processId(), event.orderId(), failed.reason());
				return;
			}

			steps.confirmAndComplete(process, event.orderId());
		}
		catch (Exception ex) {
			// 认领已提交，异常后不能让流程停在非终态：按数据库中的最新状态选择失败/补偿路径，
			// 不能依赖可能已被步骤改写的内存对象。
			log.error("履约流程执行失败，转入失败或补偿: orderId={}", event.orderId(), ex);
			routeFailure(process.processId(), event.orderId(), "系统错误: " + ex.getMessage());
		}
	}

	/**
	 * 非事务补偿编排：每个动作在各自的 {@code REQUIRES_NEW} 事务中执行，单个动作失败不会污染其他动作。
	 *
	 * <p>最后一步 {@code finalizeCancelled} 会重新读取流程并自愈到 {@code CANCELLED}。
	 * @param processId 流程 ID
	 * @param orderId 订单 ID
	 * @param reason 主失败原因
	 */
	private void compensate(ProcessId processId, OrderId orderId, String reason) {
		List<String> secondaryErrors = new ArrayList<>();
		runCompensationStep("标记补偿中", secondaryErrors, () -> steps.markCompensating(processId, reason));
		runCompensationStep("释放库存", secondaryErrors, () -> steps.releaseStock(orderId));
		runCompensationStep("取消订单", secondaryErrors, () -> steps.cancelOrder(orderId, reason));
		try {
			steps.finalizeCancelled(processId, reason, secondaryErrors);
		}
		catch (RuntimeException ex) {
			log.error("补偿收尾失败: orderId={}", orderId, ex);
		}
	}

	private void runCompensationStep(String action, List<String> secondaryErrors, Runnable step) {
		try {
			step.run();
		}
		catch (RuntimeException ex) {
			secondaryErrors.add(action + "失败: " + ex.getMessage());
			log.error("补偿动作失败: action={}", action, ex);
		}
	}

	/**
	 * 按数据库中的最新状态路由失败处理：未预占走失败，已预占/已支付/补偿中走补偿，终态忽略。
	 */
	private void routeFailure(ProcessId processId, OrderId orderId, String reason) {
		Optional<FulfillmentProcess> stored = processRepository.findById(processId);
		if (stored.isEmpty()) {
			log.error("无法加载履约流程，跳过失败处理: processId={}", processId);
			return;
		}
		FulfillmentProcess current = stored.get();
		FulfillmentStatus status = current.status();
		switch (status) {
			case STARTED -> steps.failAndCancel(current, orderId, reason);
			case STOCK_RESERVED, PAID, COMPENSATING -> compensate(processId, orderId, reason);
			default -> log.info("履约流程已处于终态，无需失败处理: orderId={}, status={}", orderId, status);
		}
	}

}
