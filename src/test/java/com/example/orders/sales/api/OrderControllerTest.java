package com.example.orders.sales.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sales.application.CreateOrderCommand;
import com.example.orders.sales.application.CreateOrderLineCommand;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sales.application.OrderView;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 销售 REST API 的端到端测试：真实 Spring 上下文、真实服务与 H2，不使用 mock。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderApplicationService orderApplicationService;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private JdbcClient jdbcClient;

	/**
	 * 清掉上一个用例遗留的未发布事件，避免共享 H2 中的毒事件阻塞本用例的 relay。
	 */
	@BeforeEach
	void clearPendingOutbox() {
		jdbcClient.sql("DELETE FROM outbox_event WHERE published_at IS NULL").update();
	}

	@Test
	void createsDraftOrder() throws Exception {
		UUID customerId = UUID.randomUUID();

		mockMvc
			.perform(post("/api/orders").contentType(JSON)
				.content("""
						{"customerId":"%s","currency":"CNY","lines":[
						  {"sku":"SKU-1","productName":"键盘","unitPrice":"199.00","quantity":2}]}
						""".formatted(customerId)))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.orderId").isNotEmpty())
			.andExpect(jsonPath("$.customerId").value(customerId.toString()))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.currency").value("CNY"))
			.andExpect(jsonPath("$.total").value(398.00))
			.andExpect(jsonPath("$.discount").value(0.00))
			.andExpect(jsonPath("$.payable").value(398.00))
			.andExpect(jsonPath("$.lines[0].sku").value("SKU-1"))
			.andExpect(jsonPath("$.lines[0].productName").value("键盘"))
			.andExpect(jsonPath("$.lines[0].quantity").value(2))
			.andExpect(jsonPath("$.lines[0].subtotal").value(398.00));
	}

	@Test
	void placeThenGet() throws Exception {
		OrderId orderId = createDraftWithOneLine();

		mockMvc.perform(post("/api/orders/{id}/place", orderId.value()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("PLACED"))
			.andExpect(jsonPath("$.placedAt").isNotEmpty());

		mockMvc.perform(get("/api/orders/{id}", orderId.value()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.status").value("PLACED"))
			.andExpect(jsonPath("$.total").value(398.00));
	}

	@Test
	void cancelWithoutReasonReturns400() throws Exception {
		OrderId orderId = createDraftWithOneLine();

		mockMvc
			.perform(post("/api/orders/{id}/cancel", orderId.value()).contentType(JSON).content("""
					{"reason":"  "}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	@Test
	void unknownOrderReturns404() throws Exception {
		UUID unknown = UUID.randomUUID();

		mockMvc.perform(get("/api/orders/{id}", unknown))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	@Test
	void cancelConfirmedOrderReturns409() throws Exception {
		OrderId orderId = createDraftWithOneLine();
		orderApplicationService.place(orderId);
		orderApplicationService.confirmOrder(orderId);

		mockMvc
			.perform(post("/api/orders/{id}/cancel", orderId.value()).contentType(JSON).content("""
					{"reason":"客户改主意"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	@Test
	void addAndRemoveLine() throws Exception {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		OrderId orderId = orderApplicationService
			.create(new CreateOrderCommand(customerId, Currency.CNY, List.of()));

		mockMvc
			.perform(post("/api/orders/{id}/lines", orderId.value()).contentType(JSON).content("""
					{"sku":"SKU-7","productName":"摄像头","unitPrice":"499.00","quantity":1}
					"""))
			.andExpect(status().isNoContent());

		OrderView withLine = orderApplicationService.get(orderId);
		UUID lineId = withLine.lines().getFirst().lineId().value();

		mockMvc.perform(delete("/api/orders/{id}/lines/{lineId}", orderId.value(), lineId))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/orders/{id}", orderId.value()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lines").isEmpty());
	}

	@Test
	void listsOrdersByCustomerFromReadModel() throws Exception {
		CustomerId customerId = CustomerId.of(UUID.randomUUID());
		Sku sku = Sku.of("SKU-" + UUID.randomUUID());
		stockItemRepository.save(StockItem.create(sku, "键盘", 0));
		inventoryService.restock(sku, 10);
		OrderId first = createDraftWithOneLine(customerId, sku);
		OrderId second = createDraftWithOneLine(customerId, sku);
		orderApplicationService.place(first);
		orderApplicationService.place(second);
		drainRelay();

		mockMvc.perform(get("/api/orders").param("customerId", customerId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].customerId").value(customerId.toString()))
			.andExpect(jsonPath("$[0].status").value("CONFIRMED"))
			.andExpect(jsonPath("$[0].lineCount").value(1))
			.andExpect(jsonPath("$[0].total").value(398.00));

		String body = mockMvc.perform(get("/api/orders").param("customerId", customerId.toString()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).contains(first.toString(), second.toString());
	}

	private void drainRelay() {
		int published;
		do {
			published = relay.relayOnce();
		}
		while (published > 0);
	}

	private OrderId createDraftWithOneLine() {
		return createDraftWithOneLine(CustomerId.of(UUID.randomUUID()), Sku.of("SKU-1"));
	}

	private OrderId createDraftWithOneLine(CustomerId customerId, Sku sku) {
		return orderApplicationService.create(new CreateOrderCommand(customerId, Currency.CNY,
				List.of(new CreateOrderLineCommand(sku, "键盘", Money.of("199.00", Currency.CNY),
						Quantity.of(2)))));
	}

}
