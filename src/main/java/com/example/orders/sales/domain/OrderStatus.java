package com.example.orders.sales.domain;

/**
 * 订单状态。
 */
public enum OrderStatus {

	/** 草稿：可增删行。 */
	DRAFT,

	/** 已下单：锁定订单行与金额。 */
	PLACED,

	/** 已确认：履约完成，不可取消。 */
	CONFIRMED,

	/** 已取消：终态。 */
	CANCELLED

}
