package com.example.orders.sharedkernel.outbox;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的消费幂等仓储。<strong>不</strong>声明事务：由调用方的事务边界决定认领是否提交。
 */
@Repository
public class JdbcProcessedEventStore implements ProcessedEventStore {

	private static final String CLAIM_SQL = """
			INSERT INTO processed_event (event_id, consumer, processed_at)
			SELECT CAST(? AS UUID), CAST(? AS VARCHAR(120)), CAST(? AS TIMESTAMP WITH TIME ZONE)
			WHERE NOT EXISTS (
			  SELECT 1 FROM processed_event WHERE event_id = ? AND consumer = ?
			)
			""";

	private final JdbcTemplate jdbcTemplate;

	private final Clock clock;

	public JdbcProcessedEventStore(JdbcTemplate jdbcTemplate, Clock clock) {
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
	}

	@Override
	public boolean tryMarkProcessed(UUID eventId, String consumer) {
		OffsetDateTime processedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		try {
			int inserted = jdbcTemplate.update(CLAIM_SQL, eventId, consumer, processedAt, eventId, consumer);
			return inserted > 0;
		}
		catch (DuplicateKeyException ex) {
			// 并发重复调用时两个事务可能同时通过 NOT EXISTS 检查，主键冲突说明已被另一调用认领。
			return false;
		}
	}

}
