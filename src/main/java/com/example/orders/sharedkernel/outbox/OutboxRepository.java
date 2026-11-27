package com.example.orders.sharedkernel.outbox;

import java.util.List;
import java.util.UUID;

/**
 * outbox_event 的持久化端口。
 */
public interface OutboxRepository {

	void append(OutboxEvent event);

	List<OutboxEvent> findUnpublished(int limit);

	void markPublished(UUID id);

}
