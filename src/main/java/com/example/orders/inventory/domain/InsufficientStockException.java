package com.example.orders.inventory.domain;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Sku;

/**
 * 可用库存不足以满足预占请求时抛出。
 */
public class InsufficientStockException extends DomainException {

	private static final long serialVersionUID = 1L;

	private final Sku sku;

	private final int requested;

	private final int available;

	public InsufficientStockException(Sku sku, int requested, int available) {
		super("库存不足: SKU " + (sku == null ? "?" : sku.value()) + " 需要 " + requested + "，可用 " + available);
		this.sku = sku;
		this.requested = requested;
		this.available = available;
	}

	public Sku sku() {
		return sku;
	}

	public int requested() {
		return requested;
	}

	public int available() {
		return available;
	}

}
