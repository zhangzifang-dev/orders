package com.example.orders.billing.application;

import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.PaymentId;

/**
 * 开票并扣款的结果：成功携带发票与支付标识，失败携带原因。
 */
public sealed interface BillingResult permits Charged, ChargeFailed {
}
