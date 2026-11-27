package com.example.orders.projections;

/**
 * 订单摘要投影自己的状态类型。
 *
 * <p>投影只依赖集成事件契约，不直接引用销售上下文的 {@code OrderStatus}；
 * 两者含义在当前版本一致，但读模型可以独立演进。
 */
public enum OrderSummaryStatus {

	PLACED, CONFIRMED, CANCELLED

}
