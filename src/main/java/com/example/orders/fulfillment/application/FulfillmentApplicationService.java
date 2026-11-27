package com.example.orders.fulfillment.application;

import java.util.List;
import java.util.Optional;

import com.example.orders.fulfillment.application.port.BillingOutcome;
import com.example.orders.fulfillment.application.port.ChargeFailed;
import com.example.orders.fulfillment.application.port.ReservationResult;
import com.example.orders.fulfillment.application.port.ReserveLine;
import com.example.orders.fulfillment.application.port.StockShortage;
import com.example.orders.fulfillment.domain.FulfillmentProcess;
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

	public FulfillmentApplicationService(FulfillmentSteps steps) {
		this.steps = steps;
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
				// 支付失败（含超时）：释放已预占库存、取消订单，并把流程推进到 CANCELLED。
				steps.compensate(process, event.orderId(), failed.reason());
				return;
			}

			steps.confirmAndComplete(process, event.orderId());
		}
		catch (Exception ex) {
			// 认领已提交，异常后不能让流程停在非终态：记录日志并转入失败终态。
			log.error("履约流程执行失败，转入失败终态: orderId={}", event.orderId(), ex);
			steps.failAndCancel(process, event.orderId(), "系统错误: " + ex.getMessage());
		}
	}

}
