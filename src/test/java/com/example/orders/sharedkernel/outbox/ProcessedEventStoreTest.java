package com.example.orders.sharedkernel.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.orders.sales.application.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ JdbcProcessedEventStore.class, FixedClockConfig.class })
class ProcessedEventStoreTest {

	@Autowired
	private ProcessedEventStore store;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void reset() {
		jdbcClient.sql("DELETE FROM processed_event").update();
	}

	@Test
	void firstCallReturnsTrueSecondReturnsFalse() {
		UUID eventId = UUID.randomUUID();

		assertThat(store.tryMarkProcessed(eventId, "billing")).isTrue();
		assertThat(store.tryMarkProcessed(eventId, "billing")).isFalse();

		assertThat(rowCount(eventId, "billing")).isEqualTo(1);
		assertThat(processedAt(eventId, "billing")).isEqualTo(FixedClockConfig.FIXED_INSTANT);
	}

	@Test
	void differentConsumerIsIndependent() {
		UUID eventId = UUID.randomUUID();

		assertThat(store.tryMarkProcessed(eventId, "billing")).isTrue();
		assertThat(store.tryMarkProcessed(eventId, "inventory")).isTrue();
		assertThat(store.tryMarkProcessed(eventId, "billing")).isFalse();
		assertThat(store.tryMarkProcessed(eventId, "inventory")).isFalse();

		assertThat(rowCount(eventId, "billing")).isEqualTo(1);
		assertThat(rowCount(eventId, "inventory")).isEqualTo(1);
	}

	@Test
	void concurrentDuplicateCallsClaimExactlyOnce() throws Exception {
		UUID eventId = UUID.randomUUID();
		CyclicBarrier barrier = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executor.submit(() -> {
				barrier.await(5, TimeUnit.SECONDS);
				return store.tryMarkProcessed(eventId, "billing");
			});
			Future<Boolean> second = executor.submit(() -> {
				barrier.await(5, TimeUnit.SECONDS);
				return store.tryMarkProcessed(eventId, "billing");
			});

			List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

			assertThat(results).containsExactlyInAnyOrder(true, false);
			assertThat(rowCount(eventId, "billing")).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private long rowCount(UUID eventId, String consumer) {
		return jdbcClient.sql("SELECT COUNT(*) FROM processed_event WHERE event_id = ? AND consumer = ?")
			.params(eventId, consumer)
			.query(Long.class)
			.single();
	}

	private Instant processedAt(UUID eventId, String consumer) {
		return jdbcClient
			.sql("SELECT processed_at FROM processed_event WHERE event_id = ? AND consumer = ?")
			.params(eventId, consumer)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

}
