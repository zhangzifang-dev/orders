package com.example.orders.projections;

import java.util.List;

import com.example.orders.sharedkernel.CustomerId;

/**
 * 订单摘要读模型的查询端口。
 */
public interface OrderSummaryQuery {

	/**
	 * 按客户查询订单摘要，只读投影表，不访问写模型聚合。
	 * @param customerId 客户 ID
	 * @return 该客户的订单摘要，按更新时间与订单 ID 排序
	 */
	List<OrderSummary> byCustomer(CustomerId customerId);

}
