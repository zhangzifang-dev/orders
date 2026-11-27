package com.example.orders.billing.application;

/**
 * 扣款失败：发票已作废，支付被拒绝或超时。
 */
public record ChargeFailed(String reason) implements BillingResult {
}
