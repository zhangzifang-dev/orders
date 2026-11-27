package com.example.orders.projections;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.example.orders.sharedkernel.CustomerId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的订单摘要查询：直接读取 {@code order_summary_view}，不经过 Spring Data 映射。
 */
@Repository
public class JdbcOrderSummaryQuery implements OrderSummaryQuery {

	private static final String BY_CUSTOMER_SQL = """
			SELECT order_id, customer_id, status, total_amount, currency, line_count, updated_at
			FROM order_summary_view
			WHERE customer_id = ?
			ORDER BY updated_at, order_id
			""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcOrderSummaryQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<OrderSummary> byCustomer(CustomerId customerId) {
		return jdbcTemplate
			.query(BY_CUSTOMER_SQL, (rs, rowNum) -> new OrderSummaryView(rs.getObject("order_id", UUID.class),
					rs.getObject("customer_id", UUID.class), rs.getString("status"), rs.getBigDecimal("total_amount"),
					rs.getString("currency"), rs.getInt("line_count"),
					rs.getObject("updated_at", OffsetDateTime.class).toInstant()),
					customerId.value())
			.stream()
			.map(OrderSummaryView::toSummary)
			.toList();
	}

}
