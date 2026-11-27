package com.example.orders.billing.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.AggregateRoot;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;

/**
 * 支付聚合根：记录针对发票的支付尝试，并维护 {@link PaymentStatus} 状态机。
 *
 * <p>状态机：{@code PENDING -> CAPTURED}；{@code PENDING -> FAILED}；{@code CAPTURED}/{@code FAILED} 为终态。
 */
public class Payment extends AggregateRoot {

	private final PaymentId paymentId;

	private final InvoiceId invoiceId;

	private final Money amount;

	private final Instant createdAt;

	private PaymentStatus status;

	private String gatewayRef;

	private String failureReason;

	private Payment(PaymentId paymentId, InvoiceId invoiceId, Money amount, Instant createdAt) {
		if (paymentId == null) {
			throw new DomainException("支付 ID 不能为空");
		}
		if (invoiceId == null) {
			throw new DomainException("发票 ID 不能为空");
		}
		if (amount == null) {
			throw new DomainException("支付金额不能为空");
		}
		if (amount.isZero() || amount.isNegative()) {
			throw new DomainException("支付金额必须大于 0: " + amount);
		}
		this.paymentId = paymentId;
		this.invoiceId = invoiceId;
		this.amount = amount;
		this.createdAt = createdAt;
		this.status = PaymentStatus.PENDING;
	}

	/**
	 * 发起支付：金额必须大于 0，初始状态为 {@link PaymentStatus#PENDING}。
	 * @param paymentId 支付 ID
	 * @param invoiceId 发票 ID
	 * @param amount 支付金额
	 * @param createdAt 创建时间
	 * @return 新创建的支付
	 */
	public static Payment request(PaymentId paymentId, InvoiceId invoiceId, Money amount, Instant createdAt) {
		return new Payment(paymentId, invoiceId, amount, createdAt);
	}

	/**
	 * 从持久化状态重建支付，不产生新事件。
	 * @param paymentId 支付 ID
	 * @param invoiceId 发票 ID
	 * @param amount 支付金额
	 * @param status 已持久化的状态
	 * @param gatewayRef 支付网关引用，未捕获时为 {@code null}
	 * @param failureReason 失败原因，未失败时为 {@code null}
	 * @param createdAt 创建时间
	 * @param version 乐观锁版本
	 * @return 重建后的支付
	 */
	public static Payment rehydrate(PaymentId paymentId, InvoiceId invoiceId, Money amount, PaymentStatus status,
			String gatewayRef, String failureReason, Instant createdAt, long version) {
		if (status == null) {
			throw new DomainException("支付状态不能为空");
		}
		Payment payment = new Payment(paymentId, invoiceId, amount, createdAt);
		payment.status = status;
		payment.gatewayRef = gatewayRef;
		payment.failureReason = failureReason;
		payment.assignVersion(version);
		return payment;
	}

	/**
	 * 捕获支付：仅允许从 {@link PaymentStatus#PENDING} 迁移到 {@link PaymentStatus#CAPTURED}。
	 * @param gatewayRef 支付网关引用，不能为空白
	 * @param occurredAt 事件发生时间
	 */
	public void capture(String gatewayRef, Instant occurredAt) {
		if (status != PaymentStatus.PENDING) {
			throw new InvalidPaymentStateException("只有待支付的支付才能捕获，当前状态: " + status);
		}
		if (gatewayRef == null || gatewayRef.isBlank()) {
			throw new DomainException("支付网关引用不能为空");
		}
		this.status = PaymentStatus.CAPTURED;
		this.gatewayRef = gatewayRef;
		registerEvent(new PaymentCaptured(UUID.randomUUID(), occurredAt, paymentId, invoiceId, amount, gatewayRef));
	}

	/**
	 * 支付失败：仅允许从 {@link PaymentStatus#PENDING} 迁移到 {@link PaymentStatus#FAILED}。
	 * @param reason 失败原因，不能为空白
	 * @param occurredAt 事件发生时间
	 */
	public void fail(String reason, Instant occurredAt) {
		if (status != PaymentStatus.PENDING) {
			throw new InvalidPaymentStateException("只有待支付的支付才能标记为失败，当前状态: " + status);
		}
		if (reason == null || reason.isBlank()) {
			throw new DomainException("支付失败原因不能为空");
		}
		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
		registerEvent(new PaymentFailed(UUID.randomUUID(), occurredAt, paymentId, invoiceId, amount, reason));
	}

	public PaymentId paymentId() {
		return paymentId;
	}

	public InvoiceId invoiceId() {
		return invoiceId;
	}

	public Money amount() {
		return amount;
	}

	public PaymentStatus status() {
		return status;
	}

	public String gatewayRef() {
		return gatewayRef;
	}

	public String failureReason() {
		return failureReason;
	}

	public Instant createdAt() {
		return createdAt;
	}

}
