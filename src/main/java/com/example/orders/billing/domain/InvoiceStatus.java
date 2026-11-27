package com.example.orders.billing.domain;

/**
 * 发票状态机：{@code ISSUED -> PAID}；{@code ISSUED -> VOIDED}；{@code PAID}/{@code VOIDED} 为终态。
 */
public enum InvoiceStatus {

	ISSUED, PAID, VOIDED

}
