package com.example.orders.projections;

/**
 * {@code order_summary_view} 投影表共用的 SQL 语句。
 *
 * <p>投影器与重建器必须使用同一份“不存在才插入”语句，避免两条写入路径在幂等语义上漂移。
 */
final class OrderSummaryStatements {

	/**
	 * 仅在订单行不存在时插入，使任意写入路径都可安全重放。
	 */
	static final String INSERT_IF_ABSENT_SQL = """
			INSERT INTO order_summary_view
			  (order_id, customer_id, status, total_amount, currency, line_count, updated_at)
			SELECT ?, ?, ?, ?, ?, ?, ?
			WHERE NOT EXISTS (SELECT 1 FROM order_summary_view WHERE order_id = ?)
			""";

	private OrderSummaryStatements() {
	}

}
