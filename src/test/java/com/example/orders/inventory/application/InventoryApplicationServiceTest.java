package com.example.orders.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.orders.config.JdbcMappingConfiguration;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.inventory.infrastructure.JdbcStockItemRepository;
import com.example.orders.sales.application.FixedClockConfig;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.IntegrationEvent;
import com.example.orders.sharedkernel.outbox.IntegrationEventRegistry;
import com.example.orders.sharedkernel.outbox.JdbcOutboxRepository;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.StockReservationFailedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.StockReservedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.StockReleasedIntegrationEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJdbcTest
@Import({ InventoryApplicationService.class, JdbcStockItemRepository.class, JdbcOutboxRepository.class,
		OutboxAppender.class, IntegrationEventRegistry.class, JdbcMappingConfiguration.class, FixedClockConfig.class,
		InventoryApplicationServiceTest.JacksonConfig.class })
class InventoryApplicationServiceTest {

	private static final Sku SKU_A = Sku.of("SKU-A");

	private static final Sku SKU_B = Sku.of("SKU-B");

	@Autowired
	private InventoryApplicationService service;

	@Autowired
	private StockItemRepository stockItemRepository;

	@Autowired
	private IntegrationEventRegistry registry;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void reservesAllLinesOrNone() {
		seedStock(SKU_A, "商品A", 5);
		seedStock(SKU_B, "商品B", 1);
		OrderId orderId = OrderId.newId();

		ReservationResult result = service.reserve(orderId,
				List.of(new ReserveLine(SKU_A, 3), new ReserveLine(SKU_B, 2)));

		assertThat(result).isInstanceOfSatisfying(StockShortage.class, shortage -> {
			assertThat(shortage.reason()).contains("库存不足");
			assertThat(shortage.reason()).contains(SKU_B.value());
		});
		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().available()).isEqualTo(5);
		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().reservedFor(orderId)).isZero();
		assertThat(stockItemRepository.findById(SKU_B).orElseThrow().available()).isEqualTo(1);
		assertThat(stockItemRepository.findById(SKU_B).orElseThrow().reservedFor(orderId)).isZero();
		assertThat(outboxEventTypes()).doesNotContain(StockReservedIntegrationEvent.EVENT_TYPE);
		assertThat(outboxEventTypes()).containsExactly(StockReservationFailedIntegrationEvent.EVENT_TYPE);
	}

	@Test
	void successfulReservationWritesOneIntegrationEventWithAllLines() {
		seedStock(SKU_A, "商品A", 5);
		seedStock(SKU_B, "商品B", 1);
		OrderId orderId = OrderId.newId();

		ReservationResult result = service.reserve(orderId,
				List.of(new ReserveLine(SKU_A, 3), new ReserveLine(SKU_B, 1)));

		assertThat(result).isInstanceOf(Reserved.class);
		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().available()).isEqualTo(2);
		assertThat(stockItemRepository.findById(SKU_B).orElseThrow().available()).isZero();
		assertThat(outboxCount()).isEqualTo(1);
		assertThat(outboxEventTypes()).containsExactly(StockReservedIntegrationEvent.EVENT_TYPE);

		IntegrationEvent event = readOutboxEvent(StockReservedIntegrationEvent.EVENT_TYPE,
				StockReservedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(StockReservedIntegrationEvent.class, reserved -> {
			assertThat(reserved.orderId()).isEqualTo(orderId);
			assertThat(reserved.occurredAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
			assertThat(reserved.lines()).hasSize(2);
			assertThat(reserved.lines())
				.extracting(line -> line.sku().value() + ":" + line.quantity())
				.containsExactlyInAnyOrder(SKU_A.value() + ":3", SKU_B.value() + ":1");
		});
	}

	@Test
	void releaseRestoresAvailabilityAndWritesReleasedEvent() {
		seedStock(SKU_A, "商品A", 5);
		OrderId orderId = OrderId.newId();
		service.reserve(orderId, List.of(new ReserveLine(SKU_A, 2)));

		service.release(orderId);

		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().available()).isEqualTo(5);
		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().reservedFor(orderId)).isZero();
		assertThat(outboxEventTypes()).contains(StockReleasedIntegrationEvent.EVENT_TYPE);

		IntegrationEvent event = readOutboxEvent(StockReleasedIntegrationEvent.EVENT_TYPE,
				StockReleasedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(StockReleasedIntegrationEvent.class, released -> {
			assertThat(released.orderId()).isEqualTo(orderId);
			assertThat(released.occurredAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
		});
	}

	@Test
	void releaseWithoutReservationDoesNotWriteEvent() {
		seedStock(SKU_A, "商品A", 5);

		service.release(OrderId.newId());

		assertThat(outboxCount()).isZero();
		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().available()).isEqualTo(5);
	}

	@Test
	void restockIncreasesAvailability() {
		seedStock(SKU_A, "商品A", 5);

		service.restock(SKU_A, 4);

		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().onHand()).isEqualTo(9);
		assertThat(stockItemRepository.findById(SKU_A).orElseThrow().available()).isEqualTo(9);
		assertThat(outboxCount()).isZero();
	}

	private void seedStock(Sku sku, String productName, int onHand) {
		stockItemRepository.save(StockItem.create(sku, productName, onHand));
	}

	private long outboxCount() {
		return jdbcClient.sql("SELECT COUNT(*) FROM outbox_event").query(Long.class).single();
	}

	private List<String> outboxEventTypes() {
		return jdbcClient.sql("SELECT event_type FROM outbox_event ORDER BY occurred_at, id")
			.query(String.class)
			.list();
	}

	private IntegrationEvent readOutboxEvent(String eventType, Class<? extends IntegrationEvent> eventClass) {
		String payload = jdbcClient.sql("SELECT payload FROM outbox_event WHERE event_type = :eventType")
			.param("eventType", eventType)
			.query(String.class)
			.single();
		IntegrationEvent event = objectMapper.readValue(payload, registry.resolve(eventType));
		assertThat(event).isInstanceOf(eventClass);
		return event;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class JacksonConfig {

		@Bean
		ObjectMapper objectMapper() {
			return JsonMapper.builder().build();
		}

	}

}
