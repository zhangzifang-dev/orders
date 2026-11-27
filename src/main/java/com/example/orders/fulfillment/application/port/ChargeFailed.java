package com.example.orders.fulfillment.application.port;

/**
 * 履约上下文视角的扣款失败结果。
 */
public record ChargeFailed(String reason) implements BillingOutcome {

	public ChargeFailed {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("失败原因不能为空");
		}
	}

}
