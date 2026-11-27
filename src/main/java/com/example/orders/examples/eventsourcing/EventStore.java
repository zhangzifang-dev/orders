package com.example.orders.examples.eventsourcing;

import java.util.List;

/**
 * 事件存储抽象：按流追加与读取事件。
 */
public interface EventStore {

	/**
	 * 向指定流追加事件。
	 * @param streamId 事件流 ID
	 * @param events 待追加事件
	 * @param expectedVersion 期望的当前流长度，用于乐观并发控制
	 */
	void append(String streamId, List<AccountEvent> events, long expectedVersion);

	/**
	 * 按追加顺序读取指定流的全部事件；流不存在时返回空列表。
	 * @param streamId 事件流 ID
	 * @return 事件列表
	 */
	List<AccountEvent> read(String streamId);

}
