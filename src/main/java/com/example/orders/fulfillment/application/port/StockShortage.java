package com.example.orders.fulfillment.application.port;

/**
 * 履约上下文视角的预占失败结果。
 */
public record StockShortage(String reason) implements ReservationResult {

	public StockShortage {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("失败原因不能为空");
		}
	}

}
