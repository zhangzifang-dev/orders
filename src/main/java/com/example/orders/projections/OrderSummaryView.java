package com.example.orders.projections;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.orders.sales.application.OrderView;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * {@code order_summary_view} 表的行快照，负责 JDBC 原始类型与读模型值对象之间的转换。
 */
public record OrderSummaryView(UUID orderId, UUID customerId, String status, BigDecimal totalAmount, String currency,
		int lineCount, Instant updatedAt) {

	/**
	 * 从写模型视图生成投影行，供读模型重建使用。
	 * @param order 订单写模型视图
	 * @param updatedAt 重建时间
	 * @return 投影行
	 */
	public static OrderSummaryView of(OrderView order, Instant updatedAt) {
		return new OrderSummaryView(order.orderId().value(), order.customerId().value(), order.statusName(),
				order.total().amount(), order.currency().code(), order.lines().size(), updatedAt);
	}

	/**
	 * 转换为读模型值对象。
	 * @return 订单摘要
	 */
	public OrderSummary toSummary() {
		return new OrderSummary(OrderId.of(orderId), CustomerId.of(customerId),
				OrderSummaryStatus.valueOf(status), Money.of(totalAmount, Currency.of(currency)), lineCount,
				updatedAt);
	}

}
