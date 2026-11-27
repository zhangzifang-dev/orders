package com.example.orders.billing.domain;

/**
 * 捕获被拒绝，携带原因。
 */
public record Declined(String reason) implements CaptureResult {

}
