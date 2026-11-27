package com.example.orders.sharedkernel;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合根基类：收集领域事件，并承载乐观锁版本。
 *
 * <p>关于 {@link #version()} 与 {@link #assignVersion(long)}：版本号本质上是持久化关注点，
 * 放在聚合根基类中是有意的务实妥协，用于支持乐观锁；它不参与领域不变量。
 */
public abstract class AggregateRoot {

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private long version;

	protected void registerEvent(DomainEvent event) {
		if (event == null) {
			throw new DomainException("领域事件不能为空");
		}
		domainEvents.add(event);
	}

	/**
	 * 返回当前收集到的领域事件，并清空内部列表。
	 * @return 不可变的事件快照
	 */
	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = List.copyOf(domainEvents);
		domainEvents.clear();
		return events;
	}

	public long version() {
		return version;
	}

	public void assignVersion(long version) {
		if (version < 0) {
			throw new DomainException("版本号不能为负数: " + version);
		}
		this.version = version;
	}

}
