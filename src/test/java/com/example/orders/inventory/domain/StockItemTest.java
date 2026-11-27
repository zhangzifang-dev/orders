package com.example.orders.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

class StockItemTest {

	private static final Sku SKU = Sku.of("SKU-1");

	@Test
	void reserveReducesAvailable() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3);

		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.available()).isEqualTo(7);
		assertThat(item.reservedFor(orderId)).isEqualTo(3);
	}

	@Test
	void cannotOverReserve() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		assertThatThrownBy(() -> item.reserve(orderId, 11))
			.isInstanceOf(InsufficientStockException.class)
			.isInstanceOf(DomainException.class);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.reservations()).isEmpty();
		assertThat(item.reservedFor(orderId)).isZero();
	}

	@Test
	void reserveIsIdempotentPerOrder() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3);
		item.reserve(orderId, 3);

		assertThat(item.available()).isEqualTo(7);
		assertThat(item.reservations()).hasSize(1);
		assertThat(item.reservedFor(orderId)).isEqualTo(3);
	}

	@Test
	void releaseIsIdempotent() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		item.reserve(orderId, 3);
		item.release(orderId);
		item.release(orderId);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.reservations()).isEmpty();
		assertThat(item.reservedFor(orderId)).isZero();
	}

	@Test
	void releaseWithoutReservationIsNoOp() {
		StockItem item = StockItem.create(SKU, "键盘", 10);

		item.release(OrderId.newId());

		assertThat(item.available()).isEqualTo(10);
	}

	@Test
	void restockRejectsNonPositive() {
		StockItem item = StockItem.create(SKU, "键盘", 10);

		assertThatThrownBy(() -> item.restock(0)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> item.restock(-5)).isInstanceOf(DomainException.class);

		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.available()).isEqualTo(10);
	}

	@Test
	void restockIncreasesOnHand() {
		StockItem item = StockItem.create(SKU, "键盘", 10);

		item.restock(5);

		assertThat(item.onHand()).isEqualTo(15);
		assertThat(item.available()).isEqualTo(15);
	}

	@Test
	void reserveRejectsNonPositiveQuantity() {
		StockItem item = StockItem.create(SKU, "键盘", 10);
		OrderId orderId = OrderId.newId();

		assertThatThrownBy(() -> item.reserve(orderId, 0)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> item.reserve(orderId, -1)).isInstanceOf(DomainException.class);

		assertThat(item.available()).isEqualTo(10);
		assertThat(item.reservations()).isEmpty();
	}

	@Test
	void createRejectsNegativeOnHand() {
		assertThatThrownBy(() -> StockItem.create(SKU, "键盘", -1)).isInstanceOf(DomainException.class);
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

}
