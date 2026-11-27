package com.example.orders.billing.infrastructure;

import org.springframework.stereotype.Component;

/**
 * 可编程的遗留支付网关模拟器。
 *
 * <p>{@link #statusCode} 默认成功码 {@code "00"}，测试可通过 {@link #setStatusCode(String)} 编程不同结果。
 */
@Component
public class SimulatedLegacyPaymentClient implements LegacyPaymentClient {

	private String statusCode = "00";

	@Override
	public LegacyResponse charge(long amountInCents, String currencyCode, String reference) {
		return new LegacyResponse(statusCode, reference, "模拟网关响应");
	}

	public String statusCode() {
		return statusCode;
	}

	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}

}
