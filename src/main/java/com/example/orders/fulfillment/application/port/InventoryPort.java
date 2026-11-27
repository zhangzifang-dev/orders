package com.example.orders.fulfillment.application.port;

import java.util.List;

import com.example.orders.sharedkernel.OrderId;

/**
 * 履约上下文访问库存上下文的端口。实现负责调用库存应用服务。
 */
public interface InventoryPort {

	ReservationResult reserve(OrderId orderId, List<ReserveLine> lines);

	void release(OrderId orderId);

}
