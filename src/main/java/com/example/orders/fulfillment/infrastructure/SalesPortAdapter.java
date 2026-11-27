package com.example.orders.fulfillment.infrastructure;

import com.example.orders.fulfillment.application.port.SalesPort;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Service;

/**
 * 销售端口适配器：只调用订单应用服务，不触碰销售领域模型。
 */
@Service
public class SalesPortAdapter implements SalesPort {

	private final OrderApplicationService orderApplicationService;

	public SalesPortAdapter(OrderApplicationService orderApplicationService) {
		this.orderApplicationService = orderApplicationService;
	}

	@Override
	public void confirm(OrderId orderId) {
		orderApplicationService.confirmOrder(orderId);
	}

	@Override
	public void cancel(OrderId orderId, String reason) {
		// 补偿可能重放：幂等判断留在销售应用边界内，适配器不接触销售领域模型。
		orderApplicationService.cancelIfNotCancelled(orderId, reason);
	}

}
