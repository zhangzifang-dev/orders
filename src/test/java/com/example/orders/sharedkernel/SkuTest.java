package com.example.orders.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SkuTest {

	@Test
	void trimsAndUppercases() {
		assertThat(Sku.of(" abc-1 ").value()).isEqualTo("ABC-1");
	}

	@Test
	void rejectsBlank() {
		assertThatThrownBy(() -> Sku.of("  ")).isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> Sku.of(null)).isInstanceOf(DomainException.class);
	}

	@Test
	void equalityIgnoresInputCaseAndWhitespace() {
		assertThat(Sku.of(" sku-1 ")).isEqualTo(Sku.of("SKU-1"));
	}

}
