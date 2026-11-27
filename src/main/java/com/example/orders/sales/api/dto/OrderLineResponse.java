package com.example.orders.sales.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 订单行的响应体，金额为十进制数字，币种由订单级 {@code currency} 给出。
 */
public record OrderLineResponse(UUID lineId, String sku, String productName, BigDecimal unitPrice, int quantity,
		BigDecimal subtotal) {
}
