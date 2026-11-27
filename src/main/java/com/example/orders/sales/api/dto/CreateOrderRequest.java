package com.example.orders.sales.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * 创建草稿订单的请求体，可一次携带若干订单行。
 */
public record CreateOrderRequest(UUID customerId, String currency, List<CreateOrderLineRequest> lines) {

	public CreateOrderRequest {
		if (lines == null) {
			lines = List.of();
		}
		else {
			for (CreateOrderLineRequest line : lines) {
				if (line == null) {
					throw new IllegalArgumentException("订单行不能为空");
				}
			}
			lines = List.copyOf(lines);
		}
	}

}
