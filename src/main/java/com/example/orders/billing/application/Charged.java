package com.example.orders.billing.application;

import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.PaymentId;

/**
 * 扣款成功：发票已支付，支付已捕获。
 */
public record Charged(InvoiceId invoiceId, PaymentId paymentId) implements BillingResult {
}
