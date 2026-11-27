package com.example.orders.sales.domain;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

/**
 * 订单行：不可变实体，携带商品快照与数量。
 */
public record OrderLine(LineId lineId, Sku sku, String productName, Money unitPrice, Quantity quantity) {

	public OrderLine {
		if (lineId == null) {
			throw new DomainException("订单行 ID 不能为空");
		}
		if (sku == null) {
			throw new DomainException("SKU 不能为空");
		}
		if (productName == null || productName.isBlank()) {
			throw new DomainException("商品名称不能为空");
		}
		if (unitPrice == null) {
			throw new DomainException("单价不能为空");
		}
		if (quantity == null) {
			throw new DomainException("数量不能为空");
		}
	}

	public Money subtotal() {
		return unitPrice.multiply(quantity.value());
	}

}
