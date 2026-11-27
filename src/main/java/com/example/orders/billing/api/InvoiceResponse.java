package com.example.orders.billing.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.orders.billing.application.InvoiceView;

/**
 * 发票详情响应体，金额以十进制数字表达，币种单独给出。
 */
public record InvoiceResponse(UUID invoiceId, UUID orderId, BigDecimal amount, String currency, String invoiceStatus,
		String paymentStatus, String gatewayRef, String failureReason) {

	static InvoiceResponse from(InvoiceView view) {
		return new InvoiceResponse(view.invoiceId().value(), view.orderId().value(), view.amount().amount(),
				view.amount().currency().code(), view.invoiceStatus().name(),
				view.paymentStatus() == null ? null : view.paymentStatus().name(), view.gatewayRef(),
				view.failureReason());
	}

}
