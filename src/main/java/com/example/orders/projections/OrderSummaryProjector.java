package com.example.orders.projections;

import static com.example.orders.projections.OrderSummaryStatements.INSERT_IF_ABSENT_SQL;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.example.orders.sharedkernel.outbox.OrderCancelledIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderConfirmedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.ProcessedEventStore;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 把销售上下文发布的集成事件投影为 {@code order_summary_view} 行。
 *
 * <p>每个监听器先通过 {@link ProcessedEventStore} 认领事件，重复投递直接忽略。
 * 投影与 outbox 发布处于同一事务：任一步失败都会一起回滚。
 */
@Component
public class OrderSummaryProjector {

	static final String CONSUMER = "order-summary";

	private static final String UPDATE_STATUS_SQL = """
			UPDATE order_summary_view SET status = ?, updated_at = ? WHERE order_id = ?
			""";

	private final JdbcTemplate jdbcTemplate;

	private final ProcessedEventStore processedEventStore;

	public OrderSummaryProjector(JdbcTemplate jdbcTemplate, ProcessedEventStore processedEventStore) {
		this.jdbcTemplate = jdbcTemplate;
		this.processedEventStore = processedEventStore;
	}

	@EventListener
	public void onOrderPlaced(OrderPlacedIntegrationEvent event) {
		if (!processedEventStore.tryMarkProcessed(event.eventId(), CONSUMER)) {
			return;
		}
		jdbcTemplate.update(INSERT_IF_ABSENT_SQL, event.orderId().value(), event.customerId().value(),
				OrderSummaryStatus.PLACED.name(), event.total().amount(), event.currency().code(), event.lines().size(),
				timestamp(event.occurredAt()), event.orderId().value());
	}

	@EventListener
	public void onOrderConfirmed(OrderConfirmedIntegrationEvent event) {
		if (!processedEventStore.tryMarkProcessed(event.eventId(), CONSUMER)) {
			return;
		}
		updateStatus(event.orderId().value(), OrderSummaryStatus.CONFIRMED, event.occurredAt());
	}

	@EventListener
	public void onOrderCancelled(OrderCancelledIntegrationEvent event) {
		if (!processedEventStore.tryMarkProcessed(event.eventId(), CONSUMER)) {
			return;
		}
		updateStatus(event.orderId().value(), OrderSummaryStatus.CANCELLED, event.occurredAt());
	}

	private void updateStatus(UUID orderId, OrderSummaryStatus status, Instant occurredAt) {
		jdbcTemplate.update(UPDATE_STATUS_SQL, status.name(), timestamp(occurredAt), orderId);
	}

	private OffsetDateTime timestamp(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
