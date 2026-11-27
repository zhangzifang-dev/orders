package com.example.orders.examples.eventsourcing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.orders.sharedkernel.DomainException;

/**
 * 内存事件存储：仅用于示例，不涉及任何持久化框架。
 *
 * <p>每个流内部版本从 1 开始；{@link #append} 以 {@code expectedVersion == 当前事件数}
 * 实现乐观并发控制，版本不匹配时拒绝写入。
 */
public class InMemoryEventStore implements EventStore {

	private final Map<String, List<StoredEvent>> streams = new HashMap<>();

	@Override
	public synchronized void append(String streamId, List<AccountEvent> events, long expectedVersion) {
		if (streamId == null || streamId.isBlank()) {
			throw new DomainException("事件流 ID 不能为空");
		}
		if (events == null || events.isEmpty()) {
			throw new DomainException("追加事件不能为空");
		}
		if (expectedVersion < 0) {
			throw new DomainException("期望版本不能为负数: " + expectedVersion);
		}

		for (AccountEvent event : events) {
			if (event == null) {
				throw new DomainException("事件不能为空");
			}
		}

		List<StoredEvent> stream = this.streams.computeIfAbsent(streamId, key -> new ArrayList<>());
		long currentVersion = stream.size();
		if (expectedVersion != currentVersion) {
			throw new DomainException(
					"事件流版本冲突: streamId=" + streamId + ", expectedVersion=" + expectedVersion
							+ ", currentVersion=" + currentVersion);
		}

		for (AccountEvent event : events) {
			stream.add(new StoredEvent(streamId, stream.size() + 1L, event));
		}
	}

	@Override
	public synchronized List<AccountEvent> read(String streamId) {
		if (streamId == null || streamId.isBlank()) {
			throw new DomainException("事件流 ID 不能为空");
		}
		List<StoredEvent> stream = this.streams.get(streamId);
		if (stream == null) {
			return List.of();
		}
		return stream.stream().map(StoredEvent::event).toList();
	}

}
