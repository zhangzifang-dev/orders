package com.example.orders.sales.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 订单详情响应体，金额以十进制数字表达，币种单独给出。
 */
public record OrderResponse(UUID orderId, UUID customerId, String status, String currency, BigDecimal total,
		BigDecimal discount, BigDecimal payable, List<OrderLineResponse> lines, Instant placedAt,
		String cancelReason) {

	public OrderResponse {
		lines = lines == null ? List.of() : List.copyOf(lines);
	}

}
