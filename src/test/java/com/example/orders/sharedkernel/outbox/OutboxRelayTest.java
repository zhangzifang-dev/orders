package com.example.orders.sharedkernel.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ JdbcOutboxRepository.class, OutboxAppender.class, OutboxRelay.class, OutboxPublisher.class,
		IntegrationEventRegistry.class, OutboxRelayTest.TestDispatcher.class, OutboxRelayTest.JacksonConfig.class })
class OutboxRelayTest {

	@Autowired
	private OutboxAppender appender;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private TestDispatcher dispatcher;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void reset() {
		jdbcClient.sql("DELETE FROM outbox_event").update();
		dispatcher.reset();
	}

	@Test
	void appendsAndPublishes() {
		OrderPlacedIntegrationEvent event = placedOrderEvent();

		appender.append(event);

		assertThat(relay.relayOnce()).isEqualTo(1);
		assertThat(dispatcher.received).hasSize(1);
		assertThat(dispatcher.received.getFirst()).isEqualTo(event);
		assertThat(relay.relayOnce()).isZero();
		assertThat(unpublishedCount()).isZero();
	}

	@Test
	void failingDispatchLeavesEventUnpublished() {
		dispatcher.failWith(new IllegalStateException("订阅者处理失败"));
		appender.append(placedOrderEvent());

		assertThat(relay.relayOnce()).isZero();

		assertThat(dispatcher.received).isEmpty();
		assertThat(unpublishedCount()).isEqualTo(1);
	}

	@Test
	void unknownEventTypeLeavesEventUnpublished() {
		appender.append(new UnknownIntegrationEvent(UUID.randomUUID(), Instant.parse("2026-11-27T10:15:30Z")));

		assertThat(relay.relayOnce()).isZero();

		assertThat(dispatcher.received).isEmpty();
		assertThat(unpublishedCount()).isEqualTo(1);
	}

	private long unpublishedCount() {
		return jdbcClient.sql("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL").query(Long.class).single();
	}

	private static OrderPlacedIntegrationEvent placedOrderEvent() {
		Currency currency = Currency.CNY;
		return new OrderPlacedIntegrationEvent(UUID.randomUUID(), Instant.parse("2026-11-27T10:15:30Z"),
				OrderId.newId(), CustomerId.of(UUID.randomUUID()), currency, Money.of("120.00", currency),
				Money.of("20.00", currency), Money.of("100.00", currency),
				List.of(new OrderLinePayload(Sku.of("kb-01"), "机械键盘", Money.of("50.00", currency), 2)));
	}

	@TestConfiguration
	static class JacksonConfig {

		@Bean
		ObjectMapper objectMapper() {
			return JsonMapper.builder().build();
		}

	}

	static final class TestDispatcher implements IntegrationEventDispatcher {

		private final List<IntegrationEvent> received = new ArrayList<>();

		private RuntimeException failure;

		@Override
		public void dispatch(IntegrationEvent event) {
			if (failure != null) {
				throw failure;
			}
			received.add(event);
		}

		void failWith(RuntimeException failure) {
			this.failure = failure;
		}

		void reset() {
			received.clear();
			failure = null;
		}

	}

	private record UnknownIntegrationEvent(UUID eventId, Instant occurredAt) implements IntegrationEvent {

		@Override
		public String eventType() {
			return "sales.order.unknown.v1";
		}

		@Override
		public String aggregateType() {
			return "sales.order";
		}

		@Override
		public String aggregateId() {
			return "unknown";
		}

	}

}
