package com.example.orders.sharedkernel.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * 跨上下文集成事件契约：相对领域事件更稳定，载荷只使用共享内核类型或原始类型。
 */
public interface IntegrationEvent {

	UUID eventId();

	String eventType();

	String aggregateType();

	String aggregateId();

	Instant occurredAt();

}
