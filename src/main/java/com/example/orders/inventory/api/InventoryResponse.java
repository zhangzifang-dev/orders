package com.example.orders.inventory.api;

import com.example.orders.inventory.application.InventoryView;

/**
 * 库存快照的响应体。
 */
public record InventoryResponse(int onHand, int reserved, int available) {

	static InventoryResponse from(InventoryView view) {
		return new InventoryResponse(view.onHand(), view.reserved(), view.available());
	}

}
