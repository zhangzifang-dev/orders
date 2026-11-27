package com.example.orders.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.application.InventoryQueryService;
import com.example.orders.inventory.application.InventoryReservationFacade;
import com.example.orders.inventory.application.ReservationResult;
import com.example.orders.inventory.application.ReserveLine;
import com.example.orders.inventory.application.Reserved;
import com.example.orders.inventory.application.StockShortage;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * 并发预占的端到端测试：两个订单同时抢最后一件库存，最多只有一个能成功。
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentReservationTest {

	private static final Sku SKU = Sku.of("SKU-LAST");

	@Autowired
	private InventoryApplicationService inventoryService;

	@Autowired
	private InventoryReservationFacade facade;

	@Autowired
	private InventoryQueryService inventory;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void seedSingleUnitOfStock() {
		jdbcClient.sql("DELETE FROM inventory_reservation WHERE sku = :sku")
			.param("sku", SKU.value())
			.update();
		jdbcClient.sql("DELETE FROM inventory_stock_item WHERE sku = :sku")
			.param("sku", SKU.value())
			.update();
		stockItemRepository.save(StockItem.create(SKU, "最后一件", 0));
		inventoryService.restock(SKU, 1);
	}

	@Test
	void twoOrdersCannotBothTakeLastItem() throws Exception {
		OrderId order1 = OrderId.newId();
		OrderId order2 = OrderId.newId();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<ReservationResult> first = pool.submit(() -> {
				start.await();
				return facade.reserve(order1, List.of(new ReserveLine(SKU, 1)));
			});
			Future<ReservationResult> second = pool.submit(() -> {
				start.await();
				return facade.reserve(order2, List.of(new ReserveLine(SKU, 1)));
			});
			start.countDown();

			List<ReservationResult> results = Stream.of(first.get(), second.get()).toList();

			assertThat(results).filteredOn(Reserved.class::isInstance).hasSize(1);
			assertThat(results).filteredOn(StockShortage.class::isInstance).hasSize(1);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(inventory.get(SKU).onHand()).isEqualTo(1);
		assertThat(inventory.get(SKU).reserved()).isEqualTo(1);
		assertThat(inventory.get(SKU).available()).isZero();
	}

}
