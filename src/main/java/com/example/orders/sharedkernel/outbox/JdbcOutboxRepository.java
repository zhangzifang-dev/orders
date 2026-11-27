package com.example.orders.sharedkernel.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的 outbox 仓储。<strong>不</strong>声明事务：由调用方的事务边界决定写入是否提交。
 */
@Repository
public class JdbcOutboxRepository implements OutboxRepository {

	private static final String INSERT_SQL = """
			INSERT INTO outbox_event (id, event_type, aggregate_type, aggregate_id, payload, occurred_at)
			VALUES (:id, :eventType, :aggregateType, :aggregateId, :payload, :occurredAt)
			""";

	private static final String FIND_UNPUBLISHED_SQL = """
			SELECT id, event_type, aggregate_type, aggregate_id, payload, occurred_at, published_at
			FROM outbox_event
			WHERE published_at IS NULL
			ORDER BY occurred_at, id
			LIMIT :limit
			""";

	private static final String MARK_PUBLISHED_SQL = """
			UPDATE outbox_event SET published_at = :publishedAt WHERE id = :id
			""";

	private final JdbcClient jdbcClient;

	private final Clock clock;

	public JdbcOutboxRepository(JdbcClient jdbcClient, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	@Override
	public void append(OutboxEvent event) {
		jdbcClient.sql(INSERT_SQL)
			.param("id", event.id())
			.param("eventType", event.eventType())
			.param("aggregateType", event.aggregateType())
			.param("aggregateId", event.aggregateId())
			.param("payload", event.payload())
			.param("occurredAt", toOffsetDateTime(event.occurredAt()))
			.update();
	}

	@Override
	public List<OutboxEvent> findUnpublished(int limit) {
		return jdbcClient.sql(FIND_UNPUBLISHED_SQL).param("limit", limit).query(this::mapRow).list();
	}

	@Override
	public void markPublished(UUID id) {
		jdbcClient.sql(MARK_PUBLISHED_SQL)
			.param("id", id)
			.param("publishedAt", toOffsetDateTime(clock.instant()))
			.update();
	}

	private OutboxEvent mapRow(ResultSet rs, int rowNumber) throws SQLException {
		return new OutboxEvent(rs.getObject("id", UUID.class), rs.getString("event_type"),
				rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getString("payload"),
				toInstant(rs.getObject("occurred_at", OffsetDateTime.class)),
				toInstant(rs.getObject("published_at", OffsetDateTime.class)));
	}

	private static Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	private static OffsetDateTime toOffsetDateTime(Instant value) {
		return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
	}

}
