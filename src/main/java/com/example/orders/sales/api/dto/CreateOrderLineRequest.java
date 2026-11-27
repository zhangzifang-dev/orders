package com.example.orders.sales.api.dto;

import java.math.BigDecimal;

/**
 * 创建订单时的一行请求。
 */
public record CreateOrderLineRequest(String sku, String productName, BigDecimal unitPrice, int quantity) {
}
