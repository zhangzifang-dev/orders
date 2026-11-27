package com.example.orders.billing.domain;

import java.util.Optional;

/**
 * 支付聚合的持久化端口。
 */
public interface PaymentRepository {

	/**
	 * 保存支付（新建或更新）。
	 * @param payment 支付聚合
	 */
	void save(Payment payment);

	/**
	 * 按发票 ID 查找支付。一个发票最多对应一次支付尝试。
	 * @param invoiceId 发票 ID
	 * @return 支付，若不存在则为空
	 */
	Optional<Payment> findByInvoiceId(InvoiceId invoiceId);

}
