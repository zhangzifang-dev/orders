package com.example.orders.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.example.orders.config.JdbcMappingConfiguration;
import com.example.orders.sales.domain.InvalidOrderStateException;
import com.example.orders.sales.domain.LineId;
import com.example.orders.sales.domain.Order;
import com.example.orders.sales.domain.OrderRepository;
import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sales.domain.TieredPricingPolicy;
import com.example.orders.sales.infrastructure.JdbcOrderRepository;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.IntegrationEvent;
import com.example.orders.sharedkernel.outbox.IntegrationEventRegistry;
import com.example.orders.sharedkernel.outbox.JdbcOutboxRepository;
import com.example.orders.sharedkernel.outbox.OrderCancelledIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderConfirmedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
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
@Import({ OrderApplicationService.class, JdbcOrderRepository.class, JdbcOutboxRepository.class,
		OutboxAppender.class, IntegrationEventRegistry.class, JdbcMappingConfiguration.class,
		TieredPricingPolicy.class, FixedClockConfig.class, OrderApplicationServiceTest.JacksonConfig.class })
class OrderApplicationServiceTest {

	private static final CustomerId CUSTOMER_ID = CustomerId.of(UUID.randomUUID());

	@Autowired
	private OrderApplicationService service;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private IntegrationEventRegistry registry;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void placeWritesOrderStateAndExactlyOneOutboxRow() {
		OrderId id = service.create(cmdWithOneLine());

		service.place(id);

		Order loaded = orderRepository.findById(id).orElseThrow();
		assertThat(loaded.status()).isEqualTo(OrderStatus.PLACED);
		assertThat(loaded.placedAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
		assertThat(loaded.discount()).isEqualTo(Money.zero(Currency.CNY));
		assertThat(outboxCount()).isEqualTo(1);
		assertThat(outboxEventTypes()).containsExactly(OrderPlacedIntegrationEvent.EVENT_TYPE);

		IntegrationEvent event = readOutboxEvent(OrderPlacedIntegrationEvent.EVENT_TYPE,
				OrderPlacedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(OrderPlacedIntegrationEvent.class, placed -> {
			assertThat(placed.orderId()).isEqualTo(id);
			assertThat(placed.customerId()).isEqualTo(CUSTOMER_ID);
			assertThat(placed.occurredAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
			assertThat(placed.total()).isEqualTo(Money.of("398.00", Currency.CNY));
			assertThat(placed.lines()).hasSize(1);
		});
	}

	@Test
	void placeWithoutLinesThrowsAndWritesNothing() {
		OrderId id = service.create(new CreateOrderCommand(CUSTOMER_ID, Currency.CNY, List.of()));

		assertThatThrownBy(() -> service.place(id)).isInstanceOf(DomainException.class);

		assertThat(orderRepository.findById(id).orElseThrow().status()).isEqualTo(OrderStatus.DRAFT);
		assertThat(outboxCount()).isZero();
	}

	@Test
	void cancelWritesCancelledEventContainingReason() {
		OrderId id = service.create(cmdWithOneLine());
		service.place(id);
		String reason = "客户改主意";

		service.cancel(id, reason);

		Order loaded = orderRepository.findById(id).orElseThrow();
		assertThat(loaded.status()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(loaded.cancelReason()).isEqualTo(reason);
		assertThat(outboxCount()).isEqualTo(2);

		IntegrationEvent event = readOutboxEvent(OrderCancelledIntegrationEvent.EVENT_TYPE,
				OrderCancelledIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(OrderCancelledIntegrationEvent.class, cancelled -> {
			assertThat(cancelled.orderId()).isEqualTo(id);
			assertThat(cancelled.reason()).isEqualTo(reason);
			assertThat(cancelled.occurredAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
		});
	}

	@Test
	void confirmOrderRejectsDraftOrder() {
		OrderId id = service.create(cmdWithOneLine());

		assertThatThrownBy(() -> service.confirmOrder(id)).isInstanceOf(InvalidOrderStateException.class);

		assertThat(orderRepository.findById(id).orElseThrow().status()).isEqualTo(OrderStatus.DRAFT);
		assertThat(outboxCount()).isZero();
	}

	@Test
	void confirmOrderFromPlacedWritesConfirmedEvent() {
		OrderId id = service.create(cmdWithOneLine());
		service.place(id);

		service.confirmOrder(id);

		assertThat(orderRepository.findById(id).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
		assertThat(outboxEventTypes()).containsExactlyInAnyOrder(OrderPlacedIntegrationEvent.EVENT_TYPE,
				OrderConfirmedIntegrationEvent.EVENT_TYPE);

		IntegrationEvent event = readOutboxEvent(OrderConfirmedIntegrationEvent.EVENT_TYPE,
				OrderConfirmedIntegrationEvent.class);
		assertThat(event).isInstanceOfSatisfying(OrderConfirmedIntegrationEvent.class,
				confirmed -> assertThat(confirmed.orderId()).isEqualTo(id));
	}

	@Test
	void addLineAndRemoveLineMutateDraftOrderWithoutOutbox() {
		OrderId id = service.create(new CreateOrderCommand(CUSTOMER_ID, Currency.CNY, List.of()));

		service.addLine(id, new AddOrderLineCommand(Sku.of("SKU-9"), "摄像头", Money.of("499.00", Currency.CNY),
				Quantity.of(1)));
		OrderView withLine = service.get(id);
		assertThat(withLine.status()).isEqualTo(OrderStatus.DRAFT);
		assertThat(withLine.lines()).hasSize(1);
		LineId lineId = withLine.lines().getFirst().lineId();

		service.removeLine(id, lineId);

		assertThat(service.get(id).lines()).isEmpty();
		assertThat(outboxCount()).isZero();
	}

	@Test
	void getAndListAllExposeOrderViews() {
		OrderId first = service.create(cmdWithOneLine());
		OrderId second = service.create(new CreateOrderCommand(CUSTOMER_ID, Currency.CNY, List.of()));

		OrderView view = service.get(first);
		assertThat(view.orderId()).isEqualTo(first);
		assertThat(view.customerId()).isEqualTo(CUSTOMER_ID);
		assertThat(view.currency()).isEqualTo(Currency.CNY);
		assertThat(view.total()).isEqualTo(Money.of("398.00", Currency.CNY));
		assertThat(view.lines()).hasSize(1);
		assertThat(view.placedAt()).isNull();
		assertThat(view.cancelReason()).isNull();

		assertThat(service.listAll()).extracting(OrderView::orderId).containsExactlyInAnyOrder(first, second);
	}

	private CreateOrderCommand cmdWithOneLine() {
		return new CreateOrderCommand(CUSTOMER_ID, Currency.CNY, List.of(new CreateOrderLineCommand(Sku.of("SKU-1"),
				"键盘", Money.of("199.00", Currency.CNY), Quantity.of(2))));
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
