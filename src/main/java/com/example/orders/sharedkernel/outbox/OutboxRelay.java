package com.example.orders.sharedkernel.outbox;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 轮询 outbox 并逐条发布。<strong>自身不开事务</strong>：每条交给 {@link OutboxPublisher#publishOne(OutboxEvent)}
 * 的独立事务，失败则回滚该条并中断本轮。
 */
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	static final int BATCH_SIZE = 50;

	private final OutboxRepository repository;

	private final OutboxPublisher publisher;

	public OutboxRelay(OutboxRepository repository, OutboxPublisher publisher) {
		this.repository = repository;
		this.publisher = publisher;
	}

	/**
	 * 发布当前批次，返回本轮成功发布的条数。
	 */
	public int relayOnce() {
		List<OutboxEvent> pending = repository.findUnpublished(BATCH_SIZE);
		int published = 0;
		for (OutboxEvent event : pending) {
			try {
				publisher.publishOne(event);
				published++;
			}
			catch (RuntimeException ex) {
				log.warn("集成事件发布失败，已回滚并中断本轮: id={}, type={}", event.id(), event.eventType(), ex);
				break;
			}
		}
		return published;
	}

}
