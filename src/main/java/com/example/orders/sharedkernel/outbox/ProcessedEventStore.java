package com.example.orders.sharedkernel.outbox;

import java.util.UUID;

/**
 * 消费幂等记录：保证同一消费者对同一事件只处理一次。
 */
public interface ProcessedEventStore {

	/**
	 * 尝试为消费者认领事件。
	 * @return 本次调用真正插入了记录时返回 {@code true}；该 (eventId, consumer) 已存在时返回
	 * {@code false}
	 */
	boolean tryMarkProcessed(UUID eventId, String consumer);

}
