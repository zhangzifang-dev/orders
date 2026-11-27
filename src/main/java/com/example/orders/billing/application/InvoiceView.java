package com.example.orders.billing.application;

import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.InvoiceStatus;
import com.example.orders.billing.domain.PaymentStatus;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 发票的只读视图：聚合发票与支付的关键状态，供 REST 层使用。
 *
 * <p>尚无支付记录时 {@code paymentStatus}、{@code gatewayRef} 与 {@code failureReason} 为 {@code null}。
 */
public record InvoiceView(InvoiceId invoiceId, OrderId orderId, Money amount, InvoiceStatus invoiceStatus,
		PaymentStatus paymentStatus, String gatewayRef, String failureReason) {
}
