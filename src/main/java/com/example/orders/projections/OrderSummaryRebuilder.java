package com.example.orders.projections;

import static com.example.orders.projections.OrderSummaryStatements.INSERT_IF_ABSENT_SQL;

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
 *
 * <p>{@code updated_at} 保留订单的事件时间（重建时使用 {@code placedAt}），而不是重建时刻；
 * 确认/取消时间无法从写模型视图恢复，重建后的该字段可能早于增量投影写入的值。
 */
@Component
public class OrderSummaryRebuilder {

	private final JdbcTemplate jdbcTemplate;

	private final OrderApplicationService orderApplicationService;

	private final TransactionTemplate transactionTemplate;

	public OrderSummaryRebuilder(JdbcTemplate jdbcTemplate, OrderApplicationService orderApplicationService,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.orderApplicationService = orderApplicationService;
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
		List<OrderSummaryView> rows = orderApplicationService.listAll()
			.stream()
			.filter(order -> order.placedAt() != null)
			.map(order -> OrderSummaryView.of(order, order.placedAt()))
			.toList();
		for (OrderSummaryView row : rows) {
			jdbcTemplate.update(INSERT_IF_ABSENT_SQL, row.orderId(), row.customerId(), row.status(),
					row.totalAmount(), row.currency(), row.lineCount(),
					OffsetDateTime.ofInstant(row.updatedAt(), ZoneOffset.UTC), row.orderId());
		}
	}

	private long count() {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_summary_view", Long.class);
		return count == null ? 0 : count;
	}

}
