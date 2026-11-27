package com.example.orders.billing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.orders.billing.domain.CaptureRequest;
import com.example.orders.billing.domain.CaptureResult;
import com.example.orders.billing.domain.Captured;
import com.example.orders.billing.domain.Declined;
import com.example.orders.billing.domain.TimedOut;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

class ExternalPaymentGatewayAdapterTest {

	private final OrderId orderId = OrderId.newId();

	@Test
	void mapsSuccessCodeToCapturedWithLegacyReference() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(new LegacyResponse("00", "legacy-ref-1", "ok"));
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		CaptureResult result = adapter.capture(request("idem-1"));

		assertThat(result).isInstanceOf(Captured.class);
		assertThat(((Captured) result).gatewayRef()).isEqualTo("legacy-ref-1");
	}

	@Test
	void mapsInsufficientFundsToDeclined() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(new LegacyResponse("51", "legacy-ref-2", "no funds"));
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		CaptureResult result = adapter.capture(request("idem-2"));

		assertThat(result).isInstanceOf(Declined.class);
		assertThat(((Declined) result).reason()).isEqualTo("余额不足");
	}

	@Test
	void mapsTimeoutCodeToTimedOut() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(new LegacyResponse("91", "legacy-ref-3", "timeout"));
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		CaptureResult result = adapter.capture(request("idem-3"));

		assertThat(result).isInstanceOf(TimedOut.class);
	}

	@Test
	void mapsUnknownCodeToDeclinedAndNeverCaptured() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(new LegacyResponse("ZZ", "legacy-ref-4", "mystery"));
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		CaptureResult result = adapter.capture(request("idem-4"));

		assertThat(result).isNotInstanceOf(Captured.class);
		assertThat(result).isInstanceOf(Declined.class);
		assertThat(((Declined) result).reason()).contains("ZZ");
	}

	@Test
	void mapsNullResponseToDeclined() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(null);
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		CaptureResult result = adapter.capture(request("idem-6"));

		assertThat(result).isNotInstanceOf(Captured.class);
		assertThat(result).isInstanceOf(Declined.class);
		assertThat(((Declined) result).reason()).contains("null");
	}

	@Test
	void mapsNullStatusCodeToDeclined() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(new LegacyResponse(null, "legacy-ref-7", "missing"));
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		CaptureResult result = adapter.capture(request("idem-7"));

		assertThat(result).isNotInstanceOf(Captured.class);
		assertThat(result).isInstanceOf(Declined.class);
		assertThat(((Declined) result).reason()).contains("null");
	}

	@Test
	void convertsYuanToCentsAndPassesIdempotencyKeyAsReference() {
		RecordingLegacyClient legacy = new RecordingLegacyClient(new LegacyResponse("00", "legacy-ref-5", "ok"));
		ExternalPaymentGatewayAdapter adapter = new ExternalPaymentGatewayAdapter(legacy);

		adapter.capture(request("idem-5"));

		assertThat(legacy.lastCharge()).isNotNull();
		assertThat(legacy.lastCharge().amountInCents()).isEqualTo(1999L);
		assertThat(legacy.lastCharge().currencyCode()).isEqualTo("CNY");
		assertThat(legacy.lastCharge().reference()).isEqualTo("idem-5");
	}

	private CaptureRequest request(String idempotencyKey) {
		return new CaptureRequest(orderId, Money.of("19.99", Currency.CNY), idempotencyKey);
	}

	private record Charge(long amountInCents, String currencyCode, String reference) {
	}

	private static final class RecordingLegacyClient implements LegacyPaymentClient {

		private final LegacyResponse response;

		private final List<Charge> charges = new ArrayList<>();

		private RecordingLegacyClient(LegacyResponse response) {
			this.response = response;
		}

		@Override
		public LegacyResponse charge(long amountInCents, String currencyCode, String reference) {
			charges.add(new Charge(amountInCents, currencyCode, reference));
			return response;
		}

		private Charge lastCharge() {
			return charges.isEmpty() ? null : charges.get(charges.size() - 1);
		}

	}

}
