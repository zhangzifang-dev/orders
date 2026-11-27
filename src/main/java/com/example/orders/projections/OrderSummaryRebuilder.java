package com.example.orders.projections;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.example.orders.sales.application.OrderApplicationService;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 读模型重建器：清空投影表后从写模型重放，用于启动兜底与人工修复。
 *
 * <p>启动时仅在视图为空时重建，避免每次启动都覆盖已有投影。使用显式事务模板，
 * 保证监听器自调用场景下清空与重放仍在同一事务内。
 */
@Component
public class OrderSummaryRebuilder {

	private static final String INSERT_SQL = """
			INSERT INTO order_summary_view
			  (order_id, customer_id, status, total_amount, currency, line_count, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbcTemplate;

	private final OrderApplicationService orderApplicationService;

	private final Clock clock;

	private final TransactionTemplate transactionTemplate;

	public OrderSummaryRebuilder(JdbcTemplate jdbcTemplate, OrderApplicationService orderApplicationService,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.orderApplicationService = orderApplicationService;
		this.clock = clock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 清空并从写模型重放全部订单摘要。
	 */
	public void rebuild() {
		transactionTemplate.executeWithoutResult(status -> doRebuild());
	}

	/**
	 * 应用启动时若投影表为空则重建一次。
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void rebuildOnStartupIfEmpty() {
		if (count() == 0) {
			rebuild();
		}
	}

	private void doRebuild() {
		jdbcTemplate.update("DELETE FROM order_summary_view");
		Instant now = clock.instant();
		OffsetDateTime updatedAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
		List<OrderSummaryView> rows = orderApplicationService.listAll()
			.stream()
			.map(order -> OrderSummaryView.of(order, now))
			.toList();
		for (OrderSummaryView row : rows) {
			jdbcTemplate.update(INSERT_SQL, row.orderId(), row.customerId(), row.status(), row.totalAmount(),
					row.currency(), row.lineCount(), updatedAt);
		}
	}

	private long count() {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_summary_view", Long.class);
		return count == null ? 0 : count;
	}

}
