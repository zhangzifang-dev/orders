package com.example.orders.fulfillment.application.port;

import com.example.orders.sharedkernel.OrderId;

/**
 * 履约上下文访问销售上下文的端口。实现负责调用订单应用服务。
 */
public interface SalesPort {

	void confirm(OrderId orderId);

	void cancel(OrderId orderId, String reason);

}
