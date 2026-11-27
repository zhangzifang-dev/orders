package com.example.orders.inventory.domain;

import java.util.List;
import java.util.Optional;

import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

/**
 * 库存聚合的仓储端口。
 */
public interface StockItemRepository {

	Optional<StockItem> findById(Sku sku);

	void save(StockItem stockItem);

	List<StockItem> findAll();

	/**
	 * 查询被指定订单预占过的库存项，用于释放预占。
	 * @param orderId 订单 ID
	 * @return 包含该订单预占的库存项
	 */
	List<StockItem> findByReservationOrderId(OrderId orderId);

}
