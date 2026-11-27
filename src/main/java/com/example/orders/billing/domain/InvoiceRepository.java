package com.example.orders.billing.domain;

import java.util.Optional;

import com.example.orders.sharedkernel.OrderId;

/**
 * 发票聚合的持久化端口。
 */
public interface InvoiceRepository {

	/**
	 * 保存发票（新建或更新）。
	 * @param invoice 发票聚合
	 */
	void save(Invoice invoice);

	/**
	 * 按发票 ID 查找。
	 * @param invoiceId 发票 ID
	 * @return 发票，若不存在则为空
	 */
	Optional<Invoice> findById(InvoiceId invoiceId);

	/**
	 * 按订单 ID 查找发票。一个订单最多对应一张发票。
	 * @param orderId 订单 ID
	 * @return 发票，若不存在则为空
	 */
	Optional<Invoice> findByOrderId(OrderId orderId);

}
