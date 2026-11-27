package com.example.orders.billing.infrastructure;

/**
 * 遗留网关响应：状态码、网关引用与消息。
 */
public record LegacyResponse(String statusCode, String reference, String message) {

}
