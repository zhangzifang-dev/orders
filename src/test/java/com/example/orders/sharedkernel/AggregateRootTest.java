package com.example.orders.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AggregateRootTest {

	@Test
	void pullsRegisteredEventsOnce() {
		TestAggregate aggregate = new TestAggregate();
		DomainEvent first = new TestEvent(UUID.randomUUID(), Instant.now(), "test.first", "1");
		DomainEvent second = new TestEvent(UUID.randomUUID(), Instant.now(), "test.second", "1");

		aggregate.raise(first);
		aggregate.raise(second);

		assertThat(aggregate.pullDomainEvents()).containsExactly(first, second);
		assertThat(aggregate.pullDomainEvents()).isEmpty();
	}

	@Test
	void pulledEventsAreImmutable() {
		TestAggregate aggregate = new TestAggregate();
		aggregate.raise(new TestEvent(UUID.randomUUID(), Instant.now(), "test.first", "1"));

		List<DomainEvent> events = aggregate.pullDomainEvents();

		assertThatThrownBy(() -> events.add(new TestEvent(UUID.randomUUID(), Instant.now(), "test.second", "1")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void versionStartsAtZeroAndCanBeAssigned() {
		TestAggregate aggregate = new TestAggregate();

		assertThat(aggregate.version()).isZero();

		aggregate.assignVersion(7L);

		assertThat(aggregate.version()).isEqualTo(7L);
	}

	@Test
	void rejectsNullEvent() {
		TestAggregate aggregate = new TestAggregate();

		assertThatThrownBy(() -> aggregate.raise(null)).isInstanceOf(DomainException.class);
	}

	private static final class TestAggregate extends AggregateRoot {

		private void raise(DomainEvent event) {
			registerEvent(event);
		}

	}

	private record TestEvent(UUID eventId, Instant occurredAt, String eventType, String aggregateId)
			implements DomainEvent {
	}

}
