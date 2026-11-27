package com.example.orders.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.orders.billing.domain.Captured;
import com.example.orders.billing.domain.Declined;
import com.example.orders.billing.domain.Invoice;
import com.example.orders.billing.domain.InvoiceRepository;
import com.example.orders.billing.domain.InvoiceStatus;
import com.example.orders.billing.domain.Payment;
import com.example.orders.billing.domain.PaymentGateway;
import com.example.orders.billing.domain.PaymentRepository;
import com.example.orders.billing.domain.PaymentStatus;
import com.example.orders.billing.domain.TimedOut;
import com.example.orders.billing.infrastructure.JdbcInvoiceRepository;
import com.example.orders.billing.infrastructure.JdbcPaymentRepository;
import com.example.orders.billing.support.FakePaymentGateway;
import com.example.orders.config.JdbcMappingConfiguration;
import com.example.orders.sales.application.FixedClockConfig;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.outbox.IntegrationEvent;
import com.example.orders.sharedkernel.outbox.IntegrationEventRegistry;
import com.example.orders.sharedkernel.outbox.InvoiceIssuedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.InvoiceVoidedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.JdbcOutboxRepository;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.PaymentCapturedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.PaymentFailedIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJdbcTest
@Import({ BillingApplicationService.class, BillingQueryService.class, JdbcInvoiceRepository.class,
		JdbcPaymentRepository.class, JdbcOutboxRepository.class, OutboxAppender.class, IntegrationEventRegistry.class,
		JdbcMappingConfiguration.class, FixedClockConfig.class, BillingApplicationServiceTest.JacksonConfig.class,
		BillingApplicationServiceTest.GatewayConfig.class })
class BillingApplicationServiceTest {

	private static final CustomerId CUSTOMER_ID = CustomerId.of(UUID.randomUUID());

	private static final Money AMOUNT = Money.of("199.00", Currency.CNY);

	@Autowired
	private BillingApplicationService service;

	@Autowired
	private BillingQueryService queryService;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	@Qualifier("fakePaymentGateway")
	private FakePaymentGateway gateway;

	@Autowired
	private IntegrationEventRegistry registry;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void resetGateway() {
		gateway.reset();
	}

	@Test
	void capturesPaymentAndMarksInvoicePaid() {
		OrderId orderId = OrderId.newId();
		gateway.enqueue(new Captured("GW-REF-1"));

		BillingResult result = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);

		Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow();
		Payment payment = paymentRepository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
		assertThat(result).isInstanceOfSatisfying(Charged.class, charged -> {
			assertThat(charged.invoiceId()).isEqualTo(invoice.invoiceId());
			assertThat(charged.paymentId()).isEqualTo(payment.paymentId());
		});
		assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAID);
		assertThat(invoice.amount()).isEqualTo(AMOUNT);
		assertThat(invoice.customerId()).isEqualTo(CUSTOMER_ID);
		assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
		assertThat(payment.gatewayRef()).isEqualTo("GW-REF-1");
		assertThat(payment.failureReason()).isNull();
		assertThat(payment.amount()).isEqualTo(AMOUNT);

		assertThat(gateway.capturedRequests()).singleElement().satisfies(request -> {
			assertThat(request.orderId()).isEqualTo(orderId);
			assertThat(request.amount()).isEqualTo(AMOUNT);
			assertThat(request.idempotencyKey()).isNotBlank();
		});

		assertThat(outboxEventTypes()).containsExactlyInAnyOrder(InvoiceIssuedIntegrationEvent.EVENT_TYPE,
				PaymentCapturedIntegrationEvent.EVENT_TYPE);
		IntegrationEvent event = readOutboxEvent(PaymentCapturedIntegrationEvent.EVENT_TYPE,
				PaymentCapturedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(PaymentCapturedIntegrationEvent.class, captured -> {
			assertThat(captured.orderId()).isEqualTo(orderId);
			assertThat(captured.invoiceId()).isEqualTo(invoice.invoiceId().value());
			assertThat(captured.paymentId()).isEqualTo(payment.paymentId().value());
			assertThat(captured.amount()).isEqualTo(AMOUNT);
			assertThat(captured.occurredAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
		});
	}

	@Test
	void declinedPaymentVoidsInvoiceAndWritesFailureEvent() {
		OrderId orderId = OrderId.newId();
		gateway.enqueue(new Declined("余额不足"));

		BillingResult result = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);

		Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow();
		Payment payment = paymentRepository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
		assertThat(result).isInstanceOfSatisfying(ChargeFailed.class, failed -> {
			assertThat(failed.reason()).isEqualTo("余额不足");
		});
		assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOIDED);
		assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.failureReason()).isEqualTo("余额不足");
		assertThat(payment.gatewayRef()).isNull();

		assertThat(gateway.capturedRequests()).hasSize(1);
		assertThat(outboxEventTypes()).containsExactlyInAnyOrder(InvoiceIssuedIntegrationEvent.EVENT_TYPE,
				InvoiceVoidedIntegrationEvent.EVENT_TYPE, PaymentFailedIntegrationEvent.EVENT_TYPE);
		IntegrationEvent event = readOutboxEvent(PaymentFailedIntegrationEvent.EVENT_TYPE,
				PaymentFailedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(PaymentFailedIntegrationEvent.class, failed -> {
			assertThat(failed.orderId()).isEqualTo(orderId);
			assertThat(failed.invoiceId()).isEqualTo(invoice.invoiceId().value());
			assertThat(failed.reason()).isEqualTo("余额不足");
			assertThat(failed.occurredAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
		});
	}

	@Test
	void timeoutIsTreatedAsFailure() {
		OrderId orderId = OrderId.newId();
		gateway.enqueue(new TimedOut());

		BillingResult result = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);

		Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow();
		Payment payment = paymentRepository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
		assertThat(result).isInstanceOfSatisfying(ChargeFailed.class,
				failed -> assertThat(failed.reason()).isEqualTo("TIMEOUT"));
		assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOIDED);
		assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.failureReason()).isEqualTo("TIMEOUT");

		assertThat(outboxEventTypes()).containsExactlyInAnyOrder(InvoiceIssuedIntegrationEvent.EVENT_TYPE,
				InvoiceVoidedIntegrationEvent.EVENT_TYPE, PaymentFailedIntegrationEvent.EVENT_TYPE);
		IntegrationEvent event = readOutboxEvent(PaymentFailedIntegrationEvent.EVENT_TYPE,
				PaymentFailedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(PaymentFailedIntegrationEvent.class,
				failed -> assertThat(failed.reason()).isEqualTo("TIMEOUT"));
	}

	@Test
	void repeatedCallForSameOrderIsIdempotent() {
		OrderId orderId = OrderId.newId();
		gateway.enqueue(new Captured("GW-REF-1"));

		BillingResult first = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);
		BillingResult second = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);

		assertThat(second).isEqualTo(first);
		assertThat(gateway.capturedRequests()).hasSize(1);
		assertThat(invoiceCount()).isEqualTo(1);
		assertThat(paymentCount()).isEqualTo(1);
		assertThat(outboxCount()).isEqualTo(2);
	}

	@Test
	void queryServiceProjectsInvoiceAndPayment() {
		OrderId orderId = OrderId.newId();
		gateway.enqueue(new Captured("GW-REF-1"));
		service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);

		InvoiceView view = queryService.getByOrder(orderId).orElseThrow();

		Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow();
		Payment payment = paymentRepository.findByInvoiceId(invoice.invoiceId()).orElseThrow();
		assertThat(view.invoiceId()).isEqualTo(invoice.invoiceId());
		assertThat(view.orderId()).isEqualTo(orderId);
		assertThat(view.amount()).isEqualTo(AMOUNT);
		assertThat(view.invoiceStatus()).isEqualTo(InvoiceStatus.PAID);
		assertThat(view.paymentStatus()).isEqualTo(PaymentStatus.CAPTURED);
		assertThat(view.gatewayRef()).isEqualTo(payment.gatewayRef());
		assertThat(view.failureReason()).isNull();
	}

	@Test
	void queryServiceReturnsEmptyForUnknownOrder() {
		assertThat(queryService.getByOrder(OrderId.newId())).isEmpty();
	}

	@Test
	void repeatedCallAfterFailureIsAlsoIdempotent() {
		OrderId orderId = OrderId.newId();
		gateway.enqueue(new Declined("余额不足"));

		BillingResult first = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);
		BillingResult second = service.issueAndCapture(orderId, CUSTOMER_ID, AMOUNT);

		assertThat(second).isEqualTo(first);
		assertThat(second).isInstanceOfSatisfying(ChargeFailed.class,
				failed -> assertThat(failed.reason()).isEqualTo("余额不足"));
		assertThat(gateway.capturedRequests()).hasSize(1);
		assertThat(invoiceCount()).isEqualTo(1);
		assertThat(paymentCount()).isEqualTo(1);
		assertThat(outboxCount()).isEqualTo(3);
	}

	private long invoiceCount() {
		return jdbcClient.sql("SELECT COUNT(*) FROM billing_invoice").query(Long.class).single();
	}

	private long paymentCount() {
		return jdbcClient.sql("SELECT COUNT(*) FROM billing_payment").query(Long.class).single();
	}

	private long outboxCount() {
		return jdbcClient.sql("SELECT COUNT(*) FROM outbox_event").query(Long.class).single();
	}

	private List<String> outboxEventTypes() {
		return jdbcClient.sql("SELECT event_type FROM outbox_event ORDER BY occurred_at, id")
			.query(String.class)
			.list();
	}

	private IntegrationEvent readOutboxEvent(String eventType, Class<? extends IntegrationEvent> eventClass) {
		String payload = jdbcClient.sql("SELECT payload FROM outbox_event WHERE event_type = :eventType")
			.param("eventType", eventType)
			.query(String.class)
			.single();
		IntegrationEvent event = objectMapper.readValue(payload, registry.resolve(eventType));
		assertThat(event).isInstanceOf(eventClass);
		return event;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class JacksonConfig {

		@Bean
		ObjectMapper objectMapper() {
			return JsonMapper.builder().build();
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class GatewayConfig {

		@Bean
		FakePaymentGateway fakePaymentGateway() {
			return new FakePaymentGateway();
		}

		@Bean
		PaymentGateway paymentGateway(FakePaymentGateway fakePaymentGateway) {
			return fakePaymentGateway;
		}

	}

}
