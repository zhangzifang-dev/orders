package com.example.orders.sharedkernel.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 把集成事件序列化成 JSON 并追加到 outbox。
 *
 * <p>刻意不声明事务：必须与调用方（应用服务）的聚合写入处于同一事务，否则 outbox 会与业务状态不一致。
 */
public class OutboxAppender {

	private final ObjectMapper objectMapper;

	private final OutboxRepository repository;

	public OutboxAppender(ObjectMapper objectMapper, OutboxRepository repository) {
		this.objectMapper = objectMapper;
		this.repository = repository;
	}

	public void append(IntegrationEvent event) {
		String payload;
		try {
			payload = objectMapper.writeValueAsString(event);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("无法序列化集成事件: " + event.eventType(), ex);
		}
		repository.append(OutboxEvent.unpublished(event, payload));
	}

}
