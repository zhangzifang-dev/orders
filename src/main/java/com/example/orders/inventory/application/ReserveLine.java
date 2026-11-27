package com.example.orders.inventory.application;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Sku;

/**
 * 预占库存的一行请求：某个 SKU 需要预占的数量。
 */
public record ReserveLine(Sku sku, int quantity) {

	public ReserveLine {
		if (sku == null) {
			throw new DomainException("SKU 不能为空");
		}
		if (quantity <= 0) {
			throw new DomainException("预占数量必须大于 0: " + quantity);
		}
	}

}
