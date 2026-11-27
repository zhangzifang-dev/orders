package com.example.orders.bootstrap;

import com.example.orders.sales.domain.PricingPolicy;
import com.example.orders.sales.domain.TieredPricingPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 销售上下文的组合根：把纯领域的定价策略暴露为 bean。
 */
@Configuration(proxyBeanMethods = false)
public class SalesConfig {

	@Bean
	PricingPolicy pricingPolicy() {
		return new TieredPricingPolicy();
	}

}
