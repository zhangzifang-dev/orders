package com.example.orders.billing.domain;

/**
 * 支付状态机：{@code PENDING -> CAPTURED}；{@code PENDING -> FAILED}；{@code CAPTURED}/{@code FAILED} 为终态。
 */
public enum PaymentStatus {

	PENDING, CAPTURED, FAILED

}
