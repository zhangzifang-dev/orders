package com.example.orders.sales.api.dto;

/**
 * 取消订单的请求体，取消原因不能为空白。
 */
public record CancelOrderRequest(String reason) {
}
