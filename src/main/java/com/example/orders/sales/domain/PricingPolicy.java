package com.example.orders.sales.domain;

import com.example.orders.sharedkernel.Money;

/**
 * 定价领域服务：根据订单内容计算折扣金额。
 *
 * <p>纯函数，无外部依赖。
 */
public interface PricingPolicy {

	/**
	 * 计算该订单应享受的折扣，返回值不得为负且不得超过订单总额。
	 * @param order 待定价订单
	 * @return 折扣金额
	 */
	Money discountFor(Order order);

}
