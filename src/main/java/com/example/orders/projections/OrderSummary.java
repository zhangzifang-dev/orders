package com.example.orders.projections;

import java.time.Instant;

import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单列表的只读投影：只保留列表页需要的字段，不加载写模型聚合。
 *
 * @param orderId 订单 ID
 * @param customerId 客户 ID
 * @param status 投影自己的订单状态
 * @param total 订单总额
 * @param lineCount 订单行数
 * @param updatedAt 最近一次投影写入所依据的订单事件时间；从写模型重建时只有 {@code placedAt}
 * 可用，因此可能早于真实的确认/取消时间
 */
public record OrderSummary(OrderId orderId, CustomerId customerId, OrderSummaryStatus status, Money total,
		int lineCount, Instant updatedAt) {
}
