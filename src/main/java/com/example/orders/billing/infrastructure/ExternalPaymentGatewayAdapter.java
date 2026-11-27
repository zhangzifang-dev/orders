package com.example.orders.billing.infrastructure;

import java.math.RoundingMode;

import com.example.orders.billing.domain.CaptureRequest;
import com.example.orders.billing.domain.CaptureResult;
import com.example.orders.billing.domain.Captured;
import com.example.orders.billing.domain.Declined;
import com.example.orders.billing.domain.PaymentGateway;
import com.example.orders.billing.domain.TimedOut;

import org.springframework.stereotype.Component;

/**
 * 支付网关防腐层：把遗留网关的“分 + 状态码”模型翻译为计费域的 {@link CaptureResult}。
 */
@Component
public class ExternalPaymentGatewayAdapter implements PaymentGateway {

	private static final String STATUS_SUCCESS = "00";

	private static final String STATUS_INSUFFICIENT_FUNDS = "51";

	private static final String STATUS_TIMEOUT = "91";

	private final LegacyPaymentClient legacyClient;

	public ExternalPaymentGatewayAdapter(LegacyPaymentClient legacyClient) {
		this.legacyClient = legacyClient;
	}

	@Override
	public CaptureResult capture(CaptureRequest request) {
		long amountInCents = request.amount()
			.amount()
			.movePointRight(2)
			.setScale(0, RoundingMode.HALF_UP)
			.longValueExact();
		LegacyResponse response = legacyClient.charge(amountInCents, request.amount().currency().code(),
				request.idempotencyKey());
		return translate(response);
	}

	private static CaptureResult translate(LegacyResponse response) {
		return switch (response.statusCode()) {
			case STATUS_SUCCESS -> new Captured(response.reference());
			case STATUS_INSUFFICIENT_FUNDS -> new Declined("余额不足");
			case STATUS_TIMEOUT -> new TimedOut();
			default -> new Declined("未知状态码: " + response.statusCode());
		};
	}

}
