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
 * 订单聚合根：维护订单行与金额，订单内所有行币种必须一致。
 *
 * <p>Task 3 只覆盖草稿、增删行与总额；状态机、折扣与定价在后续任务补充。
 */
public class Order extends AggregateRoot {

	private final OrderId orderId;

	private final CustomerId customerId;

	private final Currency currency;

	private final List<OrderLine> lines = new ArrayList<>();

	private OrderStatus status;

	private Money discount;

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

	public void addLine(LineId lineId, Sku sku, String productName, Money unitPrice, Quantity quantity,
			Instant occurredAt) {
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
	 * 应付金额；折扣逻辑在后续任务加入，当前等于总额。
	 */
	public Money payable() {
		return total();
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

	public List<OrderLine> lines() {
		return List.copyOf(lines);
	}

}
