package com.example.orders.sales.domain;

import java.math.BigDecimal;

import com.example.orders.sharedkernel.Money;

/**
 * 阶梯定价：按订单总件数打折。
 *
 * <ul>
 * <li>总件数 ≥ 20：9 折（10% 折扣）</li>
 * <li>总件数 ≥ 10：95 折（5% 折扣）</li>
 * <li>其余：无折扣</li>
 * </ul>
 */
public class TieredPricingPolicy implements PricingPolicy {

	private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");

	private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");

	@Override
	public Money discountFor(Order order) {
		int totalQuantity = order.lines().stream().mapToInt(line -> line.quantity().value()).sum();
		if (totalQuantity >= 20) {
			return order.total().multiply(TEN_PERCENT);
		}
		if (totalQuantity >= 10) {
			return order.total().multiply(FIVE_PERCENT);
		}
		return Money.zero(order.currency());
	}

}
