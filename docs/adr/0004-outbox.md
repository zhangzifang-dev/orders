# ADR-0004 · 使用事务性 Outbox 发布集成事件

- 状态：已采纳
- 日期：2026-11-27

## 背景

订单提交后需要通知 Fulfillment 启动履约；库存预占、支付结果、订单确认又会产生后续事件。系统必须保证：

- 业务状态写入与事件发布不会「一个成功、一个失败」；
- 进程崩溃后事件还能重试；
- 没有消息中间件（教学项目不引入 Kafka/RabbitMQ）；
- 消费者重复收到事件时不会重复产生副作用。

如果直接在应用服务里 `eventPublisher.publish(...)`，可能出现：

1. 数据库事务提交成功，但进程在发布前崩溃 → 事件永久丢失；
2. 事件先发出，数据库事务回滚 → 下游收到一个不存在的事实；
3. 发布失败后没有重试机制。

## 决定

采用**事务性 Outbox**：

- 业务应用服务在 `@Transactional` 内保存聚合，并把集成事件序列化写入 `outbox_event`（`OutboxAppender`，本身不加事务注解，服从调用方事务）；
- `OutboxRelay` 轮询 `published_at IS NULL` 的事件，每轮最多 50 条；
- 每条事件由 `OutboxPublisher.publishOne` 在**独立事务**中处理：反序列化 → 分发 → 标记 `published_at`；
- 分发失败时该条事务回滚，`published_at` 保持 `null`，relay 中断本轮，下一轮重试；
- 投递语义为**至少一次**；
- 消费者用 `ProcessedEventStore.tryMarkProcessed(eventId, consumer)` 做幂等认领，写入 `processed_event`；
- 进程内分发使用 `SpringEventDispatcher` 发布 Spring 事件，订阅者用 `@EventListener`；
- 集成事件类型注册在 `IntegrationEventRegistry`，relay 按 `event_type` 反序列化为具体类型。

## 后果

### 好的

- **原子性**：业务状态与待发布事件同库同事务，不会出现「状态已改、事件丢失」。
- **可重试**：发布失败不标记已发布，下一轮自动重试；进程重启后 outbox 里的未发布事件仍在。
- **可审计**：`outbox_event` 保留事件类型、聚合、payload、发生时间与发布时间，Demo 的时间线直接查询该表。
- **可测试**：测试可以注入 `OutboxRelay`，显式调用 `relayOnce()`，无需等待异步线程；`OutboxRelayTest` 验证失败回滚。
- **无中间件依赖**：单进程、单数据库即可运行，符合教学项目「一条命令跑起来」的目标。

### 坏的

- **最终一致性**：业务提交后，下游不会立即看到结果；测试与 Demo 必须显式驱动 relay。
- **重复投递**：至少一次意味着消费者必须幂等；幂等表会增长，需要清理策略（本项目未实现）。
- **轮询开销**：relay 轮询数据库；没有变更通知/CDC，空轮询也有成本。本项目甚至**没有配置定时调度器**，普通启动时 relay 不会被自动调用，只有 Demo 与测试显式驱动。
- **单条失败阻塞本轮**：`OutboxRelay` 遇到失败会 `break`，后面的同批事件要等下一轮；没有死信队列、退避或告警。
- **payload 演进风险**：JSON 存在表里，事件 schema 变更需要考虑旧数据的反序列化；本项目只做了 `.v1` 命名，没有真正的多版本反序列化。
- **进程内分发的边界**：`SpringEventDispatcher` 是同步调用，Saga 在 relay 的事务上下文中执行；Fulfillment 用 `REQUIRES_NEW` 隔离步骤事务，但这不是真正的跨进程消息传递。
- **outbox 表会膨胀**：已发布事件不会自动归档；生产系统需要分区/清理。

## 替代方案

| 方案 | 为什么不采用 |
|---|---|
| 保存后直接发布事件 | 无法保证数据库与事件原子性；进程崩溃会丢事件 |
| 两阶段提交（2PC） | 需要事务管理器与消息中间件支持，复杂度高；本项目无 broker |
| 消息中间件事务（Kafka transactions / RabbitMQ publisher confirms） | 需要引入并运维中间件；与「无中间件」目标冲突 |
| CDC（如 Debezium）抓取数据库变更 | 能解耦业务代码，但需要额外组件与表结构约定；对教学项目过重 |
| 只记录事件、不发布（纯审计） | 无法驱动 Fulfillment 与投影 |
| 事件溯源作为发布机制 | 主流程不采用，见 [ADR-0005](0005-no-event-sourcing-in-main-flow.md) |

如果未来引入消息中间件，Outbox 仍然有价值：它保证「数据库提交」与「消息最终发出」的一致性，只是 relay 从进程内分发改为发送到 broker，并把 `published_at` 的语义改为「已确认发送」。消费者幂等仍然必须保留。
