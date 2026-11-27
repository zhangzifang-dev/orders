package com.example.orders.sales.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 应用服务切片测试使用的固定时钟。
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

	public static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

	@Bean
	Clock clock() {
		return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
	}

}
