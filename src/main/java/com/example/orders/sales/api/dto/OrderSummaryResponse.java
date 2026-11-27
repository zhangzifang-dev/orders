package com.example.orders.sales.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 订单列表响应体：来自 CQRS 读模型，只含列表页需要的字段。
 */
public record OrderSummaryResponse(UUID orderId, UUID customerId, String status, BigDecimal total, String currency,
		int lineCount, Instant updatedAt) {
}
