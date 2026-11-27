package com.example.orders.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.example.orders.billing.domain.CaptureRequest;
import com.example.orders.billing.domain.CaptureResult;
import com.example.orders.billing.domain.Captured;
import com.example.orders.billing.domain.Declined;
import com.example.orders.billing.domain.Invoice;
import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.InvoiceRepository;
import com.example.orders.billing.domain.Payment;
import com.example.orders.billing.domain.PaymentGateway;
import com.example.orders.billing.domain.PaymentId;
import com.example.orders.billing.domain.PaymentRepository;
import com.example.orders.billing.domain.TimedOut;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.PaymentCapturedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.PaymentFailedIntegrationEvent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计费上下文的应用服务：开票、发起支付并捕获，在同一写事务内写入 outbox。
 *
 * <p>幂等性：一个订单只允许一张发票（{@code billing_invoice.order_id} 唯一）。重复调用时直接
 * 根据已有发票返回原结果，绝不会再次调用支付网关。
 *
 * <p>超时处理：本项目刻意把 {@link TimedOut} 视为失败并以 {@code TIMEOUT} 作废发票。真实系统
 * 通常需要通过网关查询或对账来确认超时订单的最终状态；这里为演示简化。
 */
@Service
public class BillingApplicationService {

	private static final String TIMEOUT_REASON = "TIMEOUT";

	private static final String IDEMPOTENCY_KEY_PREFIX = "billing-capture-";

	private final InvoiceRepository invoiceRepository;

	private final PaymentRepository paymentRepository;

	private final PaymentGateway paymentGateway;

	private final OutboxAppender outboxAppender;

	private final Clock clock;

	public BillingApplicationService(InvoiceRepository invoiceRepository, PaymentRepository paymentRepository,
			PaymentGateway paymentGateway, OutboxAppender outboxAppender, Clock clock) {
		this.invoiceRepository = invoiceRepository;
		this.paymentRepository = paymentRepository;
		this.paymentGateway = paymentGateway;
		this.outboxAppender = outboxAppender;
		this.clock = clock;
	}

	/**
	 * 为订单开票并扣款；同一订单重复调用返回首次结果。
	 * @param orderId 订单 ID
	 * @param customerId 客户 ID
	 * @param amount 应收金额
	 * @return 扣款成功或失败
	 */
	@Transactional
	public BillingResult issueAndCapture(OrderId orderId, CustomerId customerId, Money amount) {
		requireCommand(orderId, customerId, amount);

		Optional<Invoice> existing = invoiceRepository.findByOrderId(orderId);
		if (existing.isPresent()) {
			return existingOutcome(existing.get());
		}

		Instant occurredAt = clock.instant();
		Invoice invoice = Invoice.issue(InvoiceId.newId(), orderId, customerId, amount, occurredAt);
		Payment payment = Payment.request(PaymentId.newId(), invoice.invoiceId(), amount, occurredAt);

		CaptureRequest request = new CaptureRequest(orderId, amount, IDEMPOTENCY_KEY_PREFIX + orderId.value());
		CaptureResult result = paymentGateway.capture(request);

		return switch (result) {
			case Captured captured -> capture(invoice, payment, captured, occurredAt);
			case Declined declined -> fail(invoice, payment, declined.reason(), occurredAt);
			case TimedOut ignored -> fail(invoice, payment, TIMEOUT_REASON, occurredAt);
		};
	}

	private BillingResult capture(Invoice invoice, Payment payment, Captured captured, Instant occurredAt) {
		payment.capture(captured.gatewayRef(), occurredAt);
		invoice.markPaid(occurredAt);
		invoiceRepository.save(invoice);
		paymentRepository.save(payment);
		outboxAppender.append(new PaymentCapturedIntegrationEvent(UUID.randomUUID(), occurredAt, invoice.orderId(),
				invoice.invoiceId().value(), payment.paymentId().value(), invoice.amount()));
		return new Charged(invoice.invoiceId(), payment.paymentId());
	}

	private BillingResult fail(Invoice invoice, Payment payment, String reason, Instant occurredAt) {
		payment.fail(reason, occurredAt);
		invoice.voidIt(occurredAt);
		invoiceRepository.save(invoice);
		paymentRepository.save(payment);
		outboxAppender.append(new PaymentFailedIntegrationEvent(UUID.randomUUID(), occurredAt, invoice.orderId(),
				invoice.invoiceId().value(), reason));
		return new ChargeFailed(reason);
	}

	private BillingResult existingOutcome(Invoice invoice) {
		return switch (invoice.status()) {
			case PAID -> new Charged(invoice.invoiceId(), paymentRepository.findByInvoiceId(invoice.invoiceId())
				.orElseThrow(() -> new IllegalStateException("已支付发票缺少支付记录: " + invoice.invoiceId()))
				.paymentId());
			case VOIDED -> new ChargeFailed(paymentRepository.findByInvoiceId(invoice.invoiceId())
				.map(Payment::failureReason)
				.orElse("PAYMENT_FAILED"));
			case ISSUED -> throw new IllegalStateException("发票仍处于 ISSUED 状态，无法幂等重放: " + invoice.invoiceId());
		};
	}

	private void requireCommand(OrderId orderId, CustomerId customerId, Money amount) {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		if (customerId == null) {
			throw new DomainException("客户 ID 不能为空");
		}
		if (amount == null) {
			throw new DomainException("金额不能为空");
		}
	}

}
