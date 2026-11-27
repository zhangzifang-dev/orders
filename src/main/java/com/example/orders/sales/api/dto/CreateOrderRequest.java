package com.example.orders.sales.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * 创建草稿订单的请求体，可一次携带若干订单行。
 */
public record CreateOrderRequest(UUID customerId, String currency, List<CreateOrderLineRequest> lines) {

	public CreateOrderRequest {
		lines = lines == null ? List.of() : List.copyOf(lines);
	}

}
