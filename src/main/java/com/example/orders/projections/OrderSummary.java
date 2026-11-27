package com.example.orders.projections;

import java.time.Instant;

import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单列表的只读投影：只保留列表页需要的字段，不加载写模型聚合。
 *
 * @param orderId 订单 ID
 * @param customerId 客户 ID
 * @param status 订单状态
 * @param total 订单总额
 * @param lineCount 订单行数
 * @param updatedAt 投影最近一次更新时间
 */
public record OrderSummary(OrderId orderId, CustomerId customerId, OrderStatus status, Money total, int lineCount,
		Instant updatedAt) {
}
