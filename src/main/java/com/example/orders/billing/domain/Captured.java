package com.example.orders.billing.domain;

/**
 * 捕获成功，携带支付网关引用。
 */
public record Captured(String gatewayRef) implements CaptureResult {

}
