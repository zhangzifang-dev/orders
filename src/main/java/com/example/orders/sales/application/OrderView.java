package com.example.orders.sales.application;

import java.time.Instant;
import java.util.List;

import com.example.orders.sales.domain.LineId;
import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

/**
 * 订单的只读视图，供 REST 层与读模型重建使用。
 */
public record OrderView(OrderId orderId, CustomerId customerId, OrderStatus status, Currency currency, Money total,
		Money discount, Money payable, List<OrderLineView> lines, Instant placedAt, String cancelReason) {

	public OrderView {
		lines = List.copyOf(lines);
	}

	/**
	 * 状态名称：让读模型等外部调用方无需引用销售领域类型即可读取状态。
	 * @return 状态枚举名称
	 */
	public String statusName() {
		return status.name();
	}

	/**
	 * 订单行的只读视图。
	 */
	public record OrderLineView(LineId lineId, Sku sku, String productName, Money unitPrice, Quantity quantity,
			Money subtotal) {
	}

}
