package com.example.orders.fulfillment.application.port;

/**
 * 履约上下文自己的扣款结果，适配器负责翻译计费上下文的结果。
 */
public sealed interface BillingOutcome permits Charged, ChargeFailed {
}
