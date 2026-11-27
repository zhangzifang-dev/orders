package com.example.orders.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

class StockItemTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-01-02T00:00:00Z");

	private static final Sku SKU = Sku.of("SKU-1");

	@Test
	void reserveReducesAvailable() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3, OCCURRED_AT);

		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.available()).isEqualTo(7);
		assertThat(item.reservedFor(orderId)).isEqualTo(3);
	}

	@Test
	void cannotOverReserve() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		assertThatThrownBy(() -> item.reserve(orderId, 11, OCCURRED_AT))
			.isInstanceOf(InsufficientStockException.class)
			.isInstanceOf(DomainException.class);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.reservations()).isEmpty();
		assertThat(item.reservedFor(orderId)).isZero();
		assertThat(item.pullDomainEvents()).isEmpty();
	}

	@Test
	void reserveIsIdempotentPerOrder() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3, OCCURRED_AT);
		item.reserve(orderId, 3, LATER);

		assertThat(item.available()).isEqualTo(7);
		assertThat(item.reservations()).hasSize(1);
		assertThat(item.reservedFor(orderId)).isEqualTo(3);
		assertThat(item.pullDomainEvents()).hasSize(1);
	}

	@Test
	void releaseIsIdempotent() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3, OCCURRED_AT);
		item.release(orderId, LATER);
		item.release(orderId, LATER);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.reservations()).isEmpty();
		assertThat(item.reservedFor(orderId)).isZero();

		List<DomainEvent> events = item.pullDomainEvents();
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(StockReserved.class);
		assertThat(events.get(1)).isInstanceOf(StockReleased.class);
	}

	@Test
	void releaseWithoutReservationIsNoOp() {
		StockItem item = StockItem.create(SKU, "键盘", 10);

		item.release(OrderId.newId(), OCCURRED_AT);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.pullDomainEvents()).isEmpty();
	}

	@Test
	void restockRejectsNonPositive() {
		StockItem item = StockItem.create(SKU, "键盘", 10);

		assertThatThrownBy(() -> item.restock(0)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> item.restock(-5)).isInstanceOf(DomainException.class);

		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.available()).isEqualTo(10);
		assertThat(item.pullDomainEvents()).isEmpty();
	}

	@Test
	void restockIncreasesOnHandWithoutEvent() {
		StockItem item = StockItem.create(SKU, "键盘", 10);

		item.restock(5);

		assertThat(item.onHand()).isEqualTo(15);
		assertThat(item.available()).isEqualTo(15);
		assertThat(item.pullDomainEvents()).isEmpty();
	}

	@Test
	void reserveRejectsNonPositiveQuantity() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		assertThatThrownBy(() -> item.reserve(orderId, 0, OCCURRED_AT)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> item.reserve(orderId, -1, OCCURRED_AT)).isInstanceOf(DomainException.class);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.reservations()).isEmpty();
		assertThat(item.pullDomainEvents()).isEmpty();
	}

	@Test
	void createRejectsNegativeOnHand() {
		assertThatThrownBy(() -> StockItem.create(SKU, "键盘", -1)).isInstanceOf(DomainException.class);
	}

	@Test
	void eventsCarryProvidedInstant() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3, OCCURRED_AT);
		item.release(orderId, LATER);

		List<DomainEvent> events = item.pullDomainEvents();
		assertThat(events).hasSize(2);

		StockReserved reserved = (StockReserved) events.get(0);
		assertThat(reserved.occurredAt()).isEqualTo(OCCURRED_AT);
		assertThat(reserved.orderId()).isEqualTo(orderId);
		assertThat(reserved.sku()).isEqualTo(SKU);
		assertThat(reserved.quantity()).isEqualTo(3);
		assertThat(reserved.eventType()).isEqualTo("inventory.stock.reserved");
		assertThat(reserved.aggregateId()).isEqualTo(SKU.value());

		StockReleased released = (StockReleased) events.get(1);
		assertThat(released.occurredAt()).isEqualTo(LATER);
		assertThat(released.orderId()).isEqualTo(orderId);
		assertThat(released.sku()).isEqualTo(SKU);
		assertThat(released.quantity()).isEqualTo(3);
		assertThat(released.eventType()).isEqualTo("inventory.stock.released");
		assertThat(released.aggregateId()).isEqualTo(SKU.value());
	}

	@Test
	void rehydrateRestoresStateWithoutEvents() {
		OrderId orderId = OrderId.newId();
		StockItem item = StockItem.rehydrate(SKU, "键盘", 10, List.of(new Reservation(orderId, 3)), 7L);

		assertThat(item.version()).isEqualTo(7L);
		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.available()).isEqualTo(7);
		assertThat(item.reservedFor(orderId)).isEqualTo(3);
		assertThat(item.pullDomainEvents()).isEmpty();
	}

	@Test
	void reservationFailedEventCarriesReasonAndInstant() {
		OrderId orderId = OrderId.newId();
		StockReservationFailed event = new StockReservationFailed(UUID.randomUUID(), LATER, orderId, SKU, "库存不足");

		assertThat(event.occurredAt()).isEqualTo(LATER);
		assertThat(event.orderId()).isEqualTo(orderId);
		assertThat(event.sku()).isEqualTo(SKU);
		assertThat(event.reason()).isEqualTo("库存不足");
		assertThat(event.eventType()).isEqualTo("inventory.stock.reservation.failed");
		assertThat(event.aggregateId()).isEqualTo(SKU.value());
	}

}
