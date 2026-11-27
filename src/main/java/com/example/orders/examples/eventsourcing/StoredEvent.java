package com.example.orders.examples.eventsourcing;

import com.example.orders.sharedkernel.DomainException;

/**
 * 事件存储中的一条记录：流 ID、流内版本（从 1 开始）与事件本身。
 */
public record StoredEvent(String streamId, long version, AccountEvent event) {

	public StoredEvent {
		if (streamId == null || streamId.isBlank()) {
			throw new DomainException("事件流 ID 不能为空");
		}
		if (version < 1) {
			throw new DomainException("事件版本必须从 1 开始: " + version);
		}
		if (event == null) {
			throw new DomainException("事件不能为空");
		}
	}

}
