package com.example.orders.bootstrap;

import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.OutboxRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * outbox 的组合根：{@link OutboxAppender} 刻意不加注解，由这里显式装配。
 */
@Configuration(proxyBeanMethods = false)
public class OutboxConfig {

	@Bean
	OutboxAppender outboxAppender(ObjectMapper objectMapper, OutboxRepository outboxRepository) {
		return new OutboxAppender(objectMapper, outboxRepository);
	}

}
