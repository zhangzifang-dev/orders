package com.example.orders.sharedkernel.outbox;

/**
 * 集成事件的消费者扩展点：由后续任务提供实现（进程内发布到订阅者）。
 */
public interface IntegrationEventDispatcher {

	void dispatch(IntegrationEvent event);

}
