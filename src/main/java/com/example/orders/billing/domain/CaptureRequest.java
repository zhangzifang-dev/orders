package com.example.orders.billing.domain;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 支付捕获请求：订单、金额与幂等键。
 */
public record CaptureRequest(OrderId orderId, Money amount, String idempotencyKey) {

	public CaptureRequest {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		if (amount == null) {
			throw new DomainException("支付金额不能为空");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new DomainException("幂等键不能为空");
		}
	}

}
