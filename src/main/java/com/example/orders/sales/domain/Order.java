package com.example.orders.sales.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.AggregateRoot;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

/**
 * 订单聚合根：维护订单行、状态机与金额，订单内所有行币种必须一致。
 *
 * <p>状态机：{@code DRAFT -> PLACED -> CONFIRMED}；{@code DRAFT}/{@code PLACED} 可取消为
 * {@code CANCELLED}，{@code CONFIRMED} 不可取消，{@code CANCELLED} 为终态。
 */
public class Order extends AggregateRoot {

	private final OrderId orderId;

	private final CustomerId customerId;

	private final Currency currency;

	private final List<OrderLine> lines = new ArrayList<>();

	private OrderStatus status;

	private Money discount;

	private Instant placedAt;

	private String cancelReason;

	private Order(OrderId orderId, CustomerId customerId, Currency currency) {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		if (customerId == null) {
			throw new DomainException("客户 ID 不能为空");
		}
		if (currency == null) {
			throw new DomainException("币种不能为空");
		}
		this.orderId = orderId;
		this.customerId = customerId;
		this.currency = currency;
		this.status = OrderStatus.DRAFT;
		this.discount = Money.zero(currency);
	}

	public static Order draft(OrderId orderId, CustomerId customerId, Currency currency) {
		return new Order(orderId, customerId, currency);
	}

	/**
	 * 从持久化状态重建订单，不产生领域事件。
	 * @param orderId 订单 ID
	 * @param customerId 客户 ID
	 * @param currency 币种
	 * @param lines 订单行
	 * @param status 订单状态
	 * @param discount 折扣金额
	 * @param placedAt 下单时间，未下单时为 {@code null}
	 * @param cancelReason 取消原因，未取消时为 {@code null}
	 * @return 重建的订单
	 */
	public static Order rehydrate(OrderId orderId, CustomerId customerId, Currency currency, List<OrderLine> lines,
			OrderStatus status, Money discount, Instant placedAt, String cancelReason) {
		Order order = new Order(orderId, customerId, currency);
		if (lines != null) {
			for (OrderLine line : lines) {
				if (line == null) {
					throw new DomainException("订单行不能为空");
				}
				if (!line.unitPrice().currency().equals(currency)) {
					throw new DomainException("订单行币种必须与订单币种一致: " + currency + " vs "
							+ line.unitPrice().currency());
				}
				if (order.lines.stream().anyMatch(existing -> existing.lineId().equals(line.lineId()))) {
					throw new DomainException("订单行 ID 已存在: " + line.lineId());
				}
				order.lines.add(line);
			}
		}
		if (status == null) {
			throw new DomainException("订单状态不能为空");
		}
		if (discount == null) {
			throw new DomainException("折扣金额不能为空");
		}
		if (!discount.currency().equals(currency)) {
			throw new DomainException("折扣币种必须与订单币种一致: " + currency + " vs " + discount.currency());
		}
		order.status = status;
		order.discount = discount;
		order.placedAt = placedAt;
		order.cancelReason = cancelReason;
		return order;
	}

	public void addLine(LineId lineId, Sku sku, String productName, Money unitPrice, Quantity quantity,
			Instant occurredAt) {
		requireDraft();
		if (unitPrice == null) {
			throw new DomainException("单价不能为空");
		}
		if (!unitPrice.currency().equals(currency)) {
			throw new DomainException("订单行币种必须与订单币种一致: " + currency + " vs " + unitPrice.currency());
		}
		if (lines.stream().anyMatch(line -> line.lineId().equals(lineId))) {
			throw new DomainException("订单行 ID 已存在: " + lineId);
		}
		OrderLine line = new OrderLine(lineId, sku, productName, unitPrice, quantity);
		lines.add(line);
		registerEvent(new OrderLineAdded(UUID.randomUUID(), occurredAt, orderId, lineId, sku, productName,
				unitPrice, quantity));
	}

	public void removeLine(LineId lineId, Instant occurredAt) {
		requireDraft();
		OrderLine line = lines.stream()
			.filter(candidate -> candidate.lineId().equals(lineId))
			.findFirst()
			.orElseThrow(() -> new DomainException("订单行不存在: " + lineId));
		lines.remove(line);
		registerEvent(new OrderLineRemoved(UUID.randomUUID(), occurredAt, orderId, lineId));
	}

	public Money total() {
		Money total = Money.zero(currency);
		for (OrderLine line : lines) {
			total = total.add(line.subtotal());
		}
		return total;
	}

	/**
	 * 提交订单：锁定订单行，按定价策略计算折扣并迁移到 {@link OrderStatus#PLACED}。
	 * @param policy 定价策略
	 * @param occurredAt 事件发生时间
	 */
	public void place(PricingPolicy policy, Instant occurredAt) {
		requireDraft();
		if (lines.isEmpty()) {
			throw new DomainException("订单至少需要一行才能提交");
		}
		if (policy == null) {
			throw new DomainException("定价策略不能为空");
		}
		Money candidateDiscount = policy.discountFor(this);
		if (candidateDiscount == null) {
			throw new DomainException("折扣金额不能为空");
		}
		Money currentTotal = total();
		if (candidateDiscount.isNegative() || candidateDiscount.isGreaterThan(currentTotal)) {
			throw new DomainException("折扣金额必须在 0 与订单总额之间: " + candidateDiscount);
		}
		this.discount = candidateDiscount;
		this.status = OrderStatus.PLACED;
		this.placedAt = occurredAt;
		registerEvent(new OrderPlaced(UUID.randomUUID(), occurredAt, orderId, customerId, currency, currentTotal,
				discount, payable(), lines()));
	}

	/**
	 * 确认订单：仅允许从 {@link OrderStatus#PLACED} 迁移到 {@link OrderStatus#CONFIRMED}。
	 * @param occurredAt 事件发生时间
	 */
	public void confirm(Instant occurredAt) {
		if (status != OrderStatus.PLACED) {
			throw new InvalidOrderStateException("只有已下单的订单才能确认，当前状态: " + status);
		}
		this.status = OrderStatus.CONFIRMED;
		registerEvent(new OrderConfirmed(UUID.randomUUID(), occurredAt, orderId));
	}

	/**
	 * 取消订单：仅允许从 {@link OrderStatus#DRAFT} 或 {@link OrderStatus#PLACED} 迁移到
	 * {@link OrderStatus#CANCELLED}。
	 * @param reason 取消原因，不能为空白
	 * @param occurredAt 事件发生时间
	 */
	public void cancel(String reason, Instant occurredAt) {
		if (status != OrderStatus.DRAFT && status != OrderStatus.PLACED) {
			throw new InvalidOrderStateException("当前状态不可取消: " + status);
		}
		if (reason == null || reason.isBlank()) {
			throw new DomainException("取消原因不能为空");
		}
		this.status = OrderStatus.CANCELLED;
		this.cancelReason = reason;
		registerEvent(new OrderCancelled(UUID.randomUUID(), occurredAt, orderId, reason));
	}

	/**
	 * 应付金额：总额减折扣，且不会为负。
	 */
	public Money payable() {
		Money payable = total().subtract(discount);
		return payable.isNegative() ? Money.zero(currency) : payable;
	}

	public boolean isDraft() {
		return status == OrderStatus.DRAFT;
	}

	private void requireDraft() {
		if (status != OrderStatus.DRAFT) {
			throw new InvalidOrderStateException("只有草稿订单才能修改，当前状态: " + status);
		}
	}

	public OrderId orderId() {
		return orderId;
	}

	public CustomerId customerId() {
		return customerId;
	}

	public Currency currency() {
		return currency;
	}

	public OrderStatus status() {
		return status;
	}

	public Money discount() {
		return discount;
	}

	public Instant placedAt() {
		return placedAt;
	}

	public String cancelReason() {
		return cancelReason;
	}

	public List<OrderLine> lines() {
		return List.copyOf(lines);
	}

}
