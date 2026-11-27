package com.example.orders.sharedkernel.outbox;

import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 在<strong>独立事务</strong>中发布一条 outbox 事件：反序列化 → 分发 → 标记已发布。
 *
 * <p>必须是单独的 bean：若放在 {@link OutboxRelay} 内部自调用，{@code @Transactional} 代理将被绕过，
 * 订阅者失败时无法同时回滚分发副作用与 {@code published_at} 标记。
 */
public class OutboxPublisher {

	private final OutboxRepository repository;

	private final IntegrationEventRegistry registry;

	private final ObjectMapper objectMapper;

	private final IntegrationEventDispatcher dispatcher;

	public OutboxPublisher(OutboxRepository repository, IntegrationEventRegistry registry, ObjectMapper objectMapper,
			IntegrationEventDispatcher dispatcher) {
		this.repository = repository;
		this.registry = registry;
		this.objectMapper = objectMapper;
		this.dispatcher = dispatcher;
	}

	@Transactional
	public void publishOne(OutboxEvent outboxEvent) {
		dispatcher.dispatch(deserialize(outboxEvent));
		repository.markPublished(outboxEvent.id());
	}

	private IntegrationEvent deserialize(OutboxEvent outboxEvent) {
		Class<? extends IntegrationEvent> eventClass = registry.resolve(outboxEvent.eventType());
		try {
			return objectMapper.readValue(outboxEvent.payload(), eventClass);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("无法反序列化集成事件: " + outboxEvent.eventType(), ex);
		}
	}

}
