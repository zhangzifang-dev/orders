package com.example.orders.sharedkernel;

import java.util.Locale;

/**
 * 库存单位标识：去除首尾空白后转为大写，且不能为空。
 */
public record Sku(String value) {

	public Sku {
		if (value == null) {
			throw new DomainException("SKU 不能为空");
		}
		value = value.trim().toUpperCase(Locale.ROOT);
		if (value.isEmpty()) {
			throw new DomainException("SKU 不能为空");
		}
	}

	public static Sku of(String value) {
		return new Sku(value);
	}

}
