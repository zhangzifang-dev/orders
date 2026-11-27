package com.example.orders.bootstrap;

import com.example.orders.sharedkernel.outbox.IntegrationEventDispatcher;
import com.example.orders.sharedkernel.outbox.IntegrationEventRegistry;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.OutboxPublisher;
import com.example.orders.sharedkernel.outbox.OutboxRelay;
import com.example.orders.sharedkernel.outbox.OutboxRepository;
import com.example.orders.sharedkernel.outbox.SpringEventDispatcher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * outbox 的组合根：仓储、发布器、relay 与进程内事件分发器在此显式装配。
 *
 * <p>{@link OutboxAppender} 刻意不加注解，必须与调用方事务共用边界。
 */
@Configuration(proxyBeanMethods = false)
public class OutboxConfig {

	@Bean
	OutboxAppender outboxAppender(ObjectMapper objectMapper, OutboxRepository outboxRepository) {
		return new OutboxAppender(objectMapper, outboxRepository);
	}

	@Bean
	IntegrationEventRegistry integrationEventRegistry() {
		return new IntegrationEventRegistry();
	}

	@Bean
	IntegrationEventDispatcher integrationEventDispatcher(ApplicationEventPublisher eventPublisher) {
		return new SpringEventDispatcher(eventPublisher);
	}

	@Bean
	OutboxPublisher outboxPublisher(OutboxRepository outboxRepository, IntegrationEventRegistry registry,
			ObjectMapper objectMapper, IntegrationEventDispatcher dispatcher) {
		return new OutboxPublisher(outboxRepository, registry, objectMapper, dispatcher);
	}

	@Bean
	OutboxRelay outboxRelay(OutboxRepository outboxRepository, OutboxPublisher outboxPublisher) {
		return new OutboxRelay(outboxRepository, outboxPublisher);
	}

}
