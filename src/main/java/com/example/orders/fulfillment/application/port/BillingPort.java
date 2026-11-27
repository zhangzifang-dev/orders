package com.example.orders.fulfillment.application.port;

import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 履约上下文访问计费上下文的端口。实现负责调用计费应用服务。
 */
public interface BillingPort {

	BillingOutcome issueAndCapture(OrderId orderId, CustomerId customerId, Money amount);

}
