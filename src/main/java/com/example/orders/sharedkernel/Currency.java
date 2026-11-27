package com.example.orders.sharedkernel;

import java.util.regex.Pattern;

/**
 * ISO 4217 货币代码。
 *
 * <p>类型本身不写死币种，演示场景只提供 {@link #CNY}。
 */
public record Currency(String code) {

	private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

	public static final Currency CNY = new Currency("CNY");

	public Currency {
		if (code == null || !ISO_4217.matcher(code).matches()) {
			throw new DomainException("币种必须是 3 位大写字母的 ISO 4217 代码: " + code);
		}
	}

	public static Currency of(String code) {
		return new Currency(code);
	}

	@Override
	public String toString() {
		return code;
	}

}
