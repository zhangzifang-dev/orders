package com.example.orders.inventory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.application.ReserveLine;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 库存 REST API 的端到端测试：真实 Spring 上下文、真实服务与 H2，不使用 mock。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private InventoryApplicationService inventoryService;

	private Sku sku;

	@BeforeEach
	void seedStockWithOneReservation() {
		sku = Sku.of("SKU-API-" + UUID.randomUUID());
		stockItemRepository.save(StockItem.create(sku, "机械键盘", 0));
		inventoryService.restock(sku, 5);
		inventoryService.reserve(OrderId.newId(), List.of(new ReserveLine(sku, 2)));
	}

	@Test
	void returnsInventorySnapshot() throws Exception {
		mockMvc.perform(get("/api/inventory/{sku}", sku.value()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.onHand").value(5))
			.andExpect(jsonPath("$.reserved").value(2))
			.andExpect(jsonPath("$.available").value(3));
	}

	@Test
	void restockIncreasesAvailableQuantity() throws Exception {
		mockMvc.perform(post("/api/inventory/{sku}/stock", sku.value()).contentType(JSON).content("""
				{"quantity":4}
				""")).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/inventory/{sku}", sku.value()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.onHand").value(9))
			.andExpect(jsonPath("$.reserved").value(2))
			.andExpect(jsonPath("$.available").value(7));
	}

	@Test
	void unknownSkuReturns404() throws Exception {
		mockMvc.perform(get("/api/inventory/{sku}", "SKU-UNKNOWN"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

}
