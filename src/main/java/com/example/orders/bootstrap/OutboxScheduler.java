package com.example.orders.bootstrap;

import com.example.orders.sharedkernel.outbox.OutboxRelay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时驱动 outbox relay：默认每 200ms 发布一批未处理事件。
 *
 * <p>测试与 demo profile 通过 {@code orders.outbox.scheduler.enabled=false} 关闭本 bean，
 * 由测试或 {@code DemoRunner} 显式调用 {@link OutboxRelay#relayOnce()}，保持确定性。
 */
@Component
@ConditionalOnProperty(name = "orders.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

	private final OutboxRelay outboxRelay;

	public OutboxScheduler(OutboxRelay outboxRelay) {
		this.outboxRelay = outboxRelay;
	}

	@Scheduled(fixedDelayString = "${orders.outbox.relay-interval:200}")
	public void relayPendingEvents() {
		outboxRelay.relayOnce();
	}

}
