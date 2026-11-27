package com.example.orders.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuantityTest {

	@Test
	void acceptsLowerBound() {
		assertThat(Quantity.of(1).value()).isEqualTo(1);
	}

	@Test
	void acceptsUpperBound() {
		assertThat(Quantity.of(999).value()).isEqualTo(999);
	}

	@Test
	void rejectsZero() {
		assertThatThrownBy(() -> Quantity.of(0)).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsAboveUpperBound() {
		assertThatThrownBy(() -> Quantity.of(1000)).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsNegativeQuantity() {
		assertThatThrownBy(() -> Quantity.of(-1)).isInstanceOf(DomainException.class);
	}

}
