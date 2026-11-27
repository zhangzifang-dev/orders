package com.example.orders.fulfillment.application.port;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Sku;

/**
 * 履约上下文自己的预占请求行：某个 SKU 需要预占的数量。
 *
 * <p>刻意不复用库存上下文的命令类型，适配器负责翻译，以保持上下文边界。
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
