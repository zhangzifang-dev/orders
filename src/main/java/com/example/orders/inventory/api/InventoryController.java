package com.example.orders.inventory.api;

import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.application.InventoryQueryService;
import com.example.orders.sharedkernel.Sku;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存上下文 REST 入口：查询库存快照与补货。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryQueryService inventoryQueryService;

	private final InventoryApplicationService inventoryApplicationService;

	public InventoryController(InventoryQueryService inventoryQueryService,
			InventoryApplicationService inventoryApplicationService) {
		this.inventoryQueryService = inventoryQueryService;
		this.inventoryApplicationService = inventoryApplicationService;
	}

	@GetMapping("/{sku}")
	public InventoryResponse get(@PathVariable String sku) {
		return InventoryResponse.from(inventoryQueryService.get(Sku.of(sku)));
	}

	@PostMapping("/{sku}/stock")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restock(@PathVariable String sku, @RequestBody RestockRequest request) {
		inventoryApplicationService.restock(Sku.of(sku), request.quantity());
	}

	/**
	 * 补货请求体。
	 */
	public record RestockRequest(int quantity) {
	}

}
