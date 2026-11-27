package com.example.orders.sharedkernel.outbox;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 集成事件类型注册表：把 {@code event_type} 映射到可反序列化的具体类型。
 *
 * <p>新增事件类型只需在构造函数中加一行 {@link #register(String, Class)}。
 */
public class IntegrationEventRegistry {

	private final Map<String, Class<? extends IntegrationEvent>> eventTypes = new HashMap<>();

	public IntegrationEventRegistry() {
		register(OrderPlacedIntegrationEvent.EVENT_TYPE, OrderPlacedIntegrationEvent.class);
		register(OrderConfirmedIntegrationEvent.EVENT_TYPE, OrderConfirmedIntegrationEvent.class);
		register(OrderCancelledIntegrationEvent.EVENT_TYPE, OrderCancelledIntegrationEvent.class);
		register(StockReservedIntegrationEvent.EVENT_TYPE, StockReservedIntegrationEvent.class);
		register(StockReleasedIntegrationEvent.EVENT_TYPE, StockReleasedIntegrationEvent.class);
		register(StockReservationFailedIntegrationEvent.EVENT_TYPE, StockReservationFailedIntegrationEvent.class);
		register(PaymentCapturedIntegrationEvent.EVENT_TYPE, PaymentCapturedIntegrationEvent.class);
		register(PaymentFailedIntegrationEvent.EVENT_TYPE, PaymentFailedIntegrationEvent.class);
	}

	public void register(String eventType, Class<? extends IntegrationEvent> eventClass) {
		Objects.requireNonNull(eventType, "eventType 不能为空");
		Objects.requireNonNull(eventClass, "eventClass 不能为空");
		eventTypes.put(eventType, eventClass);
	}

	public Class<? extends IntegrationEvent> resolve(String eventType) {
		Class<? extends IntegrationEvent> eventClass = eventTypes.get(eventType);
		if (eventClass == null) {
			throw new IllegalStateException("未注册的集成事件类型: " + eventType);
		}
		return eventClass;
	}

}
