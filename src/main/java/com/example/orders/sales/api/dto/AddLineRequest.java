package com.example.orders.sales.api.dto;

import java.math.BigDecimal;

/**
 * 向草稿订单追加订单行的请求体。
 */
public record AddLineRequest(String sku, String productName, BigDecimal unitPrice, int quantity) {
}
