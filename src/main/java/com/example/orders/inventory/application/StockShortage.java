package com.example.orders.inventory.application;

/**
 * 预占失败，原因是面向使用方的中文说明。
 */
public record StockShortage(String reason) implements ReservationResult {

	public StockShortage {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("失败原因不能为空");
		}
	}

}
