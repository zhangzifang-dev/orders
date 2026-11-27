package com.example.orders.billing.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.sharedkernel.AggregateRoot;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;

/**
 * 发票聚合根：记录订单应开金额，并维护 {@link InvoiceStatus} 状态机。
 *
 * <p>状态机：{@code ISSUED -> PAID}；{@code ISSUED -> VOIDED}；{@code PAID}/{@code VOIDED} 为终态。
 */
public class Invoice extends AggregateRoot {

	private final InvoiceId invoiceId;

	private final OrderId orderId;

	private final CustomerId customerId;

	private final Money amount;

	private final Instant issuedAt;

	private InvoiceStatus status;

	private Invoice(InvoiceId invoiceId, OrderId orderId, CustomerId customerId, Money amount, Instant issuedAt) {
		if (invoiceId == null) {
			throw new DomainException("发票 ID 不能为空");
		}
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		if (customerId == null) {
			throw new DomainException("客户 ID 不能为空");
		}
		if (amount == null) {
			throw new DomainException("发票金额不能为空");
		}
		if (amount.isZero() || amount.isNegative()) {
			throw new DomainException("发票金额必须大于 0: " + amount);
		}
		this.invoiceId = invoiceId;
		this.orderId = orderId;
		this.customerId = customerId;
		this.amount = amount;
		this.issuedAt = issuedAt;
		this.status = InvoiceStatus.ISSUED;
	}

	/**
	 * 开具发票：金额必须大于 0，初始状态为 {@link InvoiceStatus#ISSUED}。
	 * @param invoiceId 发票 ID
	 * @param orderId 订单 ID
	 * @param customerId 客户 ID
	 * @param amount 发票金额
	 * @param issuedAt 开具时间
	 * @return 新开具的发票
	 */
	public static Invoice issue(InvoiceId invoiceId, OrderId orderId, CustomerId customerId, Money amount,
			Instant issuedAt) {
		Invoice invoice = new Invoice(invoiceId, orderId, customerId, amount, issuedAt);
		invoice.registerEvent(new InvoiceIssued(UUID.randomUUID(), issuedAt, invoiceId, orderId, customerId, amount));
		return invoice;
	}

	/**
	 * 标记发票已支付：仅允许从 {@link InvoiceStatus#ISSUED} 迁移到 {@link InvoiceStatus#PAID}。
	 * @param occurredAt 状态迁移时间
	 */
	public void markPaid(Instant occurredAt) {
		if (status != InvoiceStatus.ISSUED) {
			throw new InvalidPaymentStateException("只有已开具的发票才能标记为已支付，当前状态: " + status);
		}
		this.status = InvoiceStatus.PAID;
	}

	/**
	 * 作废发票：仅允许从 {@link InvoiceStatus#ISSUED} 迁移到 {@link InvoiceStatus#VOIDED}。
	 * @param occurredAt 事件发生时间
	 */
	public void voidIt(Instant occurredAt) {
		if (status != InvoiceStatus.ISSUED) {
			throw new InvalidPaymentStateException("只有已开具的发票才能作废，当前状态: " + status);
		}
		this.status = InvoiceStatus.VOIDED;
		registerEvent(new InvoiceVoided(UUID.randomUUID(), occurredAt, invoiceId));
	}

	public InvoiceId invoiceId() {
		return invoiceId;
	}

	public OrderId orderId() {
		return orderId;
	}

	public CustomerId customerId() {
		return customerId;
	}

	public Money amount() {
		return amount;
	}

	public InvoiceStatus status() {
		return status;
	}

	public Instant issuedAt() {
		return issuedAt;
	}

}
