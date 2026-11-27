package com.example.orders.billing.domain;

/**
 * 支付捕获结果：成功、被拒绝或超时。
 */
public sealed interface CaptureResult permits Captured, Declined, TimedOut {

}
