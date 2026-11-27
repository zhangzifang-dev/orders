package com.example.orders.inventory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.orders.sharedkernel.AggregateRoot;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 库存聚合根：维护某个 SKU 的在库数量与订单预占，保证不会超卖。
 *
 * <p>不变量：
 * <ul>
 * <li>{@code available = onHand - Σ reservations.quantity} 且不能为负；</li>
 * <li>同一订单重复预占是幂等的；</li>
 * <li>释放不存在的预占是幂等的；</li>
 * <li>补货数量必须大于 0。</li>
 * </ul>
 */
public class StockItem extends AggregateRoot {

	private final Sku sku;

	private final String productName;

	private final List<Reservation> reservations = new ArrayList<>();

	private int onHand;

	private StockItem(Sku sku, String productName, int onHand) {
		if (sku == null) {
			throw new DomainException("SKU 不能为空");
		}
		if (productName == null || productName.isBlank()) {
			throw new DomainException("商品名称不能为空");
		}
		if (onHand < 0) {
			throw new DomainException("在库数量不能为负数: " + onHand);
		}
		this.sku = sku;
		this.productName = productName;
		this.onHand = onHand;
	}

	public static StockItem create(Sku sku, String productName, int onHand) {
		return new StockItem(sku, productName, onHand);
	}

	/**
	 * 从持久化状态重建库存聚合，不产生领域事件。
	 * @param sku SKU
	 * @param productName 商品名称
	 * @param onHand 在库数量
	 * @param reservations 已有预占
	 * @param version 乐观锁版本
	 * @return 重建的库存聚合
	 */
	public static StockItem rehydrate(Sku sku, String productName, int onHand, List<Reservation> reservations,
			long version) {
		StockItem item = new StockItem(sku, productName, onHand);
		if (reservations != null) {
			for (Reservation reservation : reservations) {
				if (reservation == null) {
					throw new DomainException("预占不能为空");
				}
				if (item.findReservation(reservation.orderId()).isPresent()) {
					throw new DomainException("订单预占已存在: " + reservation.orderId());
				}
				item.reservations.add(reservation);
			}
		}
		if (item.available() < 0) {
			throw new DomainException("可用库存不能为负数: " + item.available());
		}
		item.assignVersion(version);
		return item;
	}

	/**
	 * 为订单预占库存：同一订单重复调用是幂等的；库存不足时抛出
	 * {@link InsufficientStockException} 且不改变状态。
	 * @param orderId 订单 ID
	 * @param quantity 预占数量，必须大于 0
	 * @param occurredAt 事件发生时间
	 */
	public void reserve(OrderId orderId, int quantity, Instant occurredAt) {
		requireOrderId(orderId);
		requireOccurredAt(occurredAt);
		if (quantity <= 0) {
			throw new DomainException("预占数量必须大于 0: " + quantity);
		}
		if (findReservation(orderId).isPresent()) {
			return;
		}
		int available = available();
		if (available < quantity) {
			throw new InsufficientStockException(sku, quantity, available);
		}
		reservations.add(new Reservation(orderId, quantity));
		registerEvent(new StockReserved(UUID.randomUUID(), occurredAt, orderId, sku, quantity));
	}

	/**
	 * 释放订单的库存预占：预占不存在时无操作、不产生事件。
	 * @param orderId 订单 ID
	 * @param occurredAt 事件发生时间
	 */
	public void release(OrderId orderId, Instant occurredAt) {
		requireOrderId(orderId);
		requireOccurredAt(occurredAt);
		Optional<Reservation> existing = findReservation(orderId);
		if (existing.isEmpty()) {
			return;
		}
		Reservation reservation = existing.get();
		reservations.remove(reservation);
		registerEvent(new StockReleased(UUID.randomUUID(), occurredAt, orderId, sku, reservation.quantity()));
	}

	/**
	 * 补货：增加在库数量，不产生领域事件。
	 * @param quantity 补货数量，必须大于 0
	 */
	public void restock(int quantity) {
		if (quantity <= 0) {
			throw new DomainException("补货数量必须大于 0: " + quantity);
		}
		this.onHand += quantity;
	}

	/**
	 * 可用库存：在库数量减去全部预占数量。
	 */
	public int available() {
		int reserved = 0;
		for (Reservation reservation : reservations) {
			reserved += reservation.quantity();
		}
		return onHand - reserved;
	}

	/**
	 * 指定订单当前预占的数量；没有预占时返回 0。
	 */
	public int reservedFor(OrderId orderId) {
		requireOrderId(orderId);
		return findReservation(orderId).map(Reservation::quantity).orElse(0);
	}

	private Optional<Reservation> findReservation(OrderId orderId) {
		return reservations.stream().filter(reservation -> reservation.orderId().equals(orderId)).findFirst();
	}

	private void requireOrderId(OrderId orderId) {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
	}

	private void requireOccurredAt(Instant occurredAt) {
		if (occurredAt == null) {
			throw new DomainException("事件时间不能为空");
		}
	}

	public Sku sku() {
		return sku;
	}

	public String productName() {
		return productName;
	}

	public int onHand() {
		return onHand;
	}

	public List<Reservation> reservations() {
		return List.copyOf(reservations);
	}

}
