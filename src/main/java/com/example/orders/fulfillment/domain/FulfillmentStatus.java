package com.example.orders.fulfillment.domain;

/**
 * 履约流程状态。
 */
public enum FulfillmentStatus {

	/** 流程已启动，尚未预占库存。 */
	STARTED,

	/** 库存已预占。 */
	STOCK_RESERVED,

	/** 支付已完成。 */
	PAID,

	/** 履约完成：终态。 */
	COMPLETED,

	/** 正在补偿（释放库存或退款）。 */
	COMPENSATING,

	/** 补偿完成，流程取消：终态。 */
	CANCELLED,

	/** 流程失败：终态。 */
	FAILED

}
