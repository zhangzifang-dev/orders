package com.example.orders.inventory.application;

import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sharedkernel.NotFoundException;
import com.example.orders.sharedkernel.Sku;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存查询服务：把库存聚合投影为只读快照。
 */
@Service
public class InventoryQueryService {

	private final StockItemRepository stockItemRepository;

	public InventoryQueryService(StockItemRepository stockItemRepository) {
		this.stockItemRepository = stockItemRepository;
	}

	@Transactional(readOnly = true)
	public InventoryView get(Sku sku) {
		StockItem item = stockItemRepository.findById(sku)
			.orElseThrow(() -> new NotFoundException("库存不存在: " + sku.value()));
		return InventoryView.from(item);
	}

}
