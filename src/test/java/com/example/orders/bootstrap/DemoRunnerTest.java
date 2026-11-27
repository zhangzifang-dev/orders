package com.example.orders.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 演示场景的端到端测试：真实 Spring 上下文、真实服务与 H2，不使用 Mockito。
 *
 * <p>验证成功路径与支付失败补偿路径的全部关键集成事件都出现在时间线中。
 */
@SpringBootTest(properties = "demo.run-on-startup=false")
@ActiveProfiles({ "test", "demo" })
class DemoRunnerTest {

	@Autowired
	private DemoRunner demo;

	@Test
	void printsBothScenarios() {
		String out = demo.runScenarios();

		assertThat(out).contains("sales.order.placed.v1", "inventory.stock.reserved.v1",
				"billing.payment.captured.v1", "sales.order.confirmed.v1", "fulfillment.completed.v1");
		assertThat(out).contains("billing.payment.failed.v1", "inventory.stock.released.v1",
				"sales.order.cancelled.v1");
	}

}
