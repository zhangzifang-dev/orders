package com.example.orders.sharedkernel.outbox;

import org.springframework.context.ApplicationEventPublisher;

/**
 * 进程内集成事件分发器：把 outbox 事件作为 Spring 应用事件发布，由 {@code @EventListener} 订阅者消费。
 */
public class SpringEventDispatcher implements IntegrationEventDispatcher {

	private final ApplicationEventPublisher eventPublisher;

	public SpringEventDispatcher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	@Override
	public void dispatch(IntegrationEvent event) {
		eventPublisher.publishEvent(event);
	}

}
