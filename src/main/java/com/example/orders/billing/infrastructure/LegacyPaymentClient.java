package com.example.orders.billing.infrastructure;

/**
 * 模拟遗留支付网关的客户端契约。
 *
 * <p>遗留系统以“分”为单位传输金额，并以状态码表达结果，防腐层负责翻译。
 */
public interface LegacyPaymentClient {

	/**
	 * 发起扣款。
	 * @param amountInCents 金额（分）
	 * @param currencyCode ISO 4217 币种代码
	 * @param reference 商户引用（对应幂等键）
	 * @return 遗留网关响应
	 */
	LegacyResponse charge(long amountInCents, String currencyCode, String reference);

}
