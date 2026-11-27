package com.example.orders.sharedkernel;

import java.time.Instant;
import java.util.UUID;

/**
 * 聚合内部产生的领域事件。
 */
public interface DomainEvent {

	UUID eventId();

	Instant occurredAt();

	String eventType();

	String aggregateId();

}
