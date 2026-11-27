package com.example.orders.sales.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import com.example.orders.config.JdbcMappingConfiguration;
import com.example.orders.sales.domain.LineId;
import com.example.orders.sales.domain.Order;
import com.example.orders.sales.domain.OrderRepository;
import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sales.domain.TieredPricingPolicy;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

@DataJdbcTest
@Import({ JdbcOrderRepository.class, JdbcMappingConfiguration.class })
class JdbcOrderRepositoryTest {

	private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	private OrderRepository orderRepository;

	@Test
	void roundTripsOrderWithLines() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(2),
				OCCURRED_AT);
		order.addLine(LineId.newId(), Sku.of("SKU-2"), "鼠标", Money.of("99.00", Currency.CNY), Quantity.of(1),
				OCCURRED_AT);
		order.place(new TieredPricingPolicy(), OCCURRED_AT);

		orderRepository.save(order);

		Order loaded = orderRepository.findById(order.orderId()).orElseThrow();

		assertThat(loaded.orderId()).isEqualTo(order.orderId());
		assertThat(loaded.customerId()).isEqualTo(order.customerId());
		assertThat(loaded.status()).isEqualTo(OrderStatus.PLACED);
		assertThat(loaded.placedAt()).isEqualTo(OCCURRED_AT);
		assertThat(loaded.total()).isEqualTo(Money.of("497.00", Currency.CNY));
		assertThat(loaded.payable()).isEqualTo(Money.of("497.00", Currency.CNY));
		assertThat(loaded.discount()).isEqualTo(Money.zero(Currency.CNY));
		assertThat(loaded.lines()).hasSize(2);
		assertThat(loaded.lines()).extracting(line -> line.sku().value())
			.containsExactlyInAnyOrder("SKU-1", "SKU-2");
		assertThat(loaded.version()).isEqualTo(order.version());
		assertThat(orderRepository.findByCustomerId(order.customerId())).hasSize(1);
		assertThat(orderRepository.findByCustomerId(CustomerId.of(UUID.randomUUID()))).isEmpty();
	}

	@Test
	void optimisticLockRejectsStaleWrite() {
		Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
		order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(1),
				OCCURRED_AT);
		orderRepository.save(order);

		Order first = orderRepository.findById(order.orderId()).orElseThrow();
		Order stale = orderRepository.findById(order.orderId()).orElseThrow();

		first.addLine(LineId.newId(), Sku.of("SKU-2"), "鼠标", Money.of("99.00", Currency.CNY), Quantity.of(1),
				OCCURRED_AT);
		orderRepository.save(first);

		stale.addLine(LineId.newId(), Sku.of("SKU-3"), "显示器", Money.of("999.00", Currency.CNY), Quantity.of(1),
				OCCURRED_AT);
		assertThatThrownBy(() -> orderRepository.save(stale))
			.isInstanceOf(OptimisticLockingFailureException.class);
	}

}
