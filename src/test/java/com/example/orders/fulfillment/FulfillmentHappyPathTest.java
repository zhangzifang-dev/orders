package com.example.orders.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.example.orders.fulfillment.application.FulfillmentQueryService;
import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.application.InventoryQueryService;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sales.application.CreateOrderCommand;
import com.example.orders.sales.application.CreateOrderLineCommand;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.OutboxRelay;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 履约成功路径的端到端测试：真实 Spring 上下文、真实服务与 H2，不使用 mock。
 *
 * <p>下单后由 outbox relay 发布 {@code OrderPlaced}，履约监听器依次预占库存、扣款、确认订单并完成流程。
 */
@SpringBootTest
@ActiveProfiles("test")
class FulfillmentHappyPathTest {

	@Autowired
	private OrderApplicationService orderApi;

	@Autowired
	private FulfillmentQueryService fulfillmentApi;

	@Autowired
	private InventoryQueryService inventoryApi;

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private OutboxRelay relay;

	@Test
	void orderPlacedRunsThroughToCompletion() {
		Sku sku = Sku.of("SKU-" + UUID.randomUUID());
		stockItemRepository.save(StockItem.create(sku, "机械键盘", 0));
		inventoryService.restock(sku, 5);

		OrderId id = orderApi.create(new CreateOrderCommand(CustomerId.of(UUID.randomUUID()), Currency.CNY,
				List.of(new CreateOrderLineCommand(sku, "机械键盘", Money.of("199.00", Currency.CNY),
						Quantity.of(2)))));

		orderApi.place(id);
		relay.relayOnce();

		assertThat(orderApi.get(id).status()).isEqualTo(OrderStatus.CONFIRMED);
		assertThat(fulfillmentApi.get(id).status()).isEqualTo("COMPLETED");
		assertThat(inventoryApi.get(sku).reserved()).isEqualTo(2);
	}

}
