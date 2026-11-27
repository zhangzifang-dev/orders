package com.example.orders.fulfillment.infrastructure;

import com.example.orders.billing.application.BillingApplicationService;
import com.example.orders.billing.application.BillingResult;
import com.example.orders.fulfillment.application.port.BillingOutcome;
import com.example.orders.fulfillment.application.port.BillingPort;
import com.example.orders.fulfillment.application.port.ChargeFailed;
import com.example.orders.fulfillment.application.port.Charged;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Service;

/**
 * 计费端口适配器：调用计费应用服务，并把计费上下文的结果翻译成履约上下文自己的类型。
 */
@Service
public class BillingPortAdapter implements BillingPort {

	private final BillingApplicationService billingApplicationService;

	public BillingPortAdapter(BillingApplicationService billingApplicationService) {
		this.billingApplicationService = billingApplicationService;
	}

	@Override
	public BillingOutcome issueAndCapture(OrderId orderId, CustomerId customerId, Money amount) {
		BillingResult result = billingApplicationService.issueAndCapture(orderId, customerId, amount);
		return switch (result) {
			case com.example.orders.billing.application.Charged ignored -> new Charged(orderId);
			case com.example.orders.billing.application.ChargeFailed failed -> new ChargeFailed(failed.reason());
		};
	}

}
