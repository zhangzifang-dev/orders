package com.example.orders.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent.OrderLinePayload;
import org.junit.jupiter.api.Test;

/**
 * 证明其他上下文（此处在 outbox 包之外）能完整读取公开集成事件的行载荷。
 */
class OrderPlacedIntegrationEventContractTest {

	@Test
	void crossContextConsumerCanReadLinePayload() {
		Currency currency = Currency.CNY;
		OrderPlacedIntegrationEvent event = new OrderPlacedIntegrationEvent(UUID.randomUUID(),
				Instant.parse("2026-11-27T10:15:30Z"), OrderId.newId(), CustomerId.of(UUID.randomUUID()), currency,
				Money.of("120.00", currency), Money.of("20.00", currency), Money.of("100.00", currency),
				List.of(new OrderLinePayload(Sku.of("kb-01"), "机械键盘", Money.of("50.00", currency), 2)));

		assertThat(event.eventType()).isEqualTo("sales.order.placed.v1");
		assertThat(event.aggregateType()).isEqualTo("sales.order");

		OrderLinePayload line = event.lines().getFirst();
		assertThat(line.sku()).isEqualTo(Sku.of("KB-01"));
		assertThat(line.productName()).isEqualTo("机械键盘");
		assertThat(line.unitPrice()).isEqualTo(Money.of("50.00", currency));
		assertThat(line.quantity()).isEqualTo(2);
	}

}
