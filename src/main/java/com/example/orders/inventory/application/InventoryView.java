package com.example.orders.inventory.application;

import com.example.orders.inventory.domain.Reservation;
import com.example.orders.inventory.domain.StockItem;

/**
 * 库存快照读模型：在库数量、已预占数量与可用数量。
 */
public record InventoryView(int onHand, int reserved, int available) {

	/**
	 * 从库存聚合生成快照。
	 * @param item 库存聚合
	 * @return 快照
	 */
	public static InventoryView from(StockItem item) {
		int reserved = item.reservations().stream().mapToInt(Reservation::quantity).sum();
		return new InventoryView(item.onHand(), reserved, item.available());
	}

}
