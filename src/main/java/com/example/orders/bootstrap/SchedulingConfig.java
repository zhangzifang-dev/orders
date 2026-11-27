package com.example.orders.bootstrap;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时调度。{@link OutboxScheduler} 通过属性 {@code orders.outbox.scheduler.enabled}
 * 独立开关，测试与 demo profile 会关闭它，以便显式驱动 relay。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
