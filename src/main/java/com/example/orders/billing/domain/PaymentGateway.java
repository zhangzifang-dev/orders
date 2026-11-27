package com.example.orders.billing.domain;

/**
 * 支付网关端口：由基础设施层提供具体实现（防腐层）。
 */
public interface PaymentGateway {

	/**
	 * 发起支付捕获。
	 * @param request 捕获请求
	 * @return 捕获结果
	 */
	CaptureResult capture(CaptureRequest request);

}
