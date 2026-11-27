package com.example.orders.billing.api;

import java.util.UUID;

import com.example.orders.billing.application.BillingQueryService;
import com.example.orders.sharedkernel.NotFoundException;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计费上下文 REST 入口：按订单查询发票与支付状态。
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

	private final BillingQueryService billingQueryService;

	public InvoiceController(BillingQueryService billingQueryService) {
		this.billingQueryService = billingQueryService;
	}

	@GetMapping("/{orderId}")
	public InvoiceResponse getByOrder(@PathVariable UUID orderId) {
		OrderId id = OrderId.of(orderId);
		return billingQueryService.getByOrder(id)
			.map(InvoiceResponse::from)
			.orElseThrow(() -> new NotFoundException("发票不存在: " + orderId));
	}

}
