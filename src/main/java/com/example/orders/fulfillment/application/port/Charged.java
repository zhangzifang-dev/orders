package com.example.orders.fulfillment.application.port;

import com.example.orders.sharedkernel.OrderId;

/**
 * 履约上下文视角的扣款成功结果。
 */
public record Charged(OrderId orderId) implements BillingOutcome {
}
