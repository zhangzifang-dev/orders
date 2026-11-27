package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * outbox_event 表的一行。
 *
 * @param payload 集成事件的 JSON 序列化结果
 * @param publishedAt 尚未发布时为 {@code null}
 */
public record OutboxEvent(UUID id, String eventType, String aggregateType, String aggregateId, String payload,
		Instant occurredAt, Instant publishedAt) {

	public static OutboxEvent unpublished(IntegrationEvent event, String payload) {
		return new OutboxEvent(event.eventId(), event.eventType(), event.aggregateType(), event.aggregateId(), payload,
				event.occurredAt(), null);
	}

}
