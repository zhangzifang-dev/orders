package com.example.orders.examples.eventsourcing;

import java.time.Instant;

/**
 * 账户事件：事件溯源示例中不可变的事实记录。
 */
public sealed interface AccountEvent permits AccountOpened, MoneyDeposited, MoneyWithdrawn {

	Instant occurredAt();

}
