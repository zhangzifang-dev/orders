# 06 · 事件与一致性

## 概念

### 领域事件 vs 集成事件

| | 领域事件（Domain Event） | 集成事件（Integration Event） |
|---|---|---|
| 产生位置 | 聚合内部 | 应用层，根据领域事件翻译 |
| 范围 | 限界上下文内部 | 跨上下文 |
| 命名 | 过去式，无版本后缀 | 带版本，如 `.v1` |
| 载荷 | 可以携带领域对象（如 `List<OrderLine>`） | 只使用稳定契约类型或原始类型 |
| 消费者 | 本上下文（也可以只作为审计/测试断言） | 其他上下文、投影、外部系统 |
| 变更影响 | 只影响本上下文 | 影响所有订阅者，需要版本意识 |

**两者不能共用同一个类。** 否则 Sales 为了内部重构修改 `OrderLine`，会顺手破坏跨上下文契约。本项目每个集成事件都在 `com.example.orders.sharedkernel.outbox` 中单独定义。

### 事务性 Outbox

业务状态与「待发布事件」必须在**同一个数据库事务**中提交，否则会出现：

- 业务写成功、事件丢失；
- 事件发出、业务回滚。

Outbox 的做法是：在业务事务里向 `outbox_event` 插一行；另一个中继进程/线程轮询未发布行，分发成功后标记 `published_at`。投递语义是**至少一次**，消费者必须幂等。

### 最终一致性

事件是异步/后置的，所以下游状态不会与上游在同一个瞬间一致。例如订单已经 `PLACED`，但 `order_summary_view` 可能还没有这一行；等 relay 发布并投影后才会出现。这是可接受的**最终一致**，不是 bug。

## 本项目在哪

### 事件类型清单（真实字符串）

领域事件（无版本）：

| 领域事件 | `eventType()` | 聚合 |
|---|---|---|
| `OrderPlaced` | `sales.order.placed` | `Order` |
| `OrderConfirmed` | `sales.order.confirmed` | `Order` |
| `OrderCancelled` | `sales.order.cancelled` | `Order` |
| `OrderLineAdded` | `sales.order.line-added` | `Order` |
| `OrderLineRemoved` | `sales.order.line-removed` | `Order` |
| `InvoiceIssued` | `billing.invoice.issued` | `Invoice` |
| `InvoiceVoided` | `billing.invoice.voided` | `Invoice` |
| `PaymentCaptured` | `billing.payment.captured` | `Payment` |
| `PaymentFailed` | `billing.payment.failed` | `Payment` |

集成事件（带 `.v1`，全部在 `sharedkernel/outbox`）：

| 集成事件 | `EVENT_TYPE` | `aggregateType` | 主要来源 |
|---|---|---|---|
| `OrderPlacedIntegrationEvent` | `sales.order.placed.v1` | `sales.order` | Sales |
| `OrderConfirmedIntegrationEvent` | `sales.order.confirmed.v1` | `sales.order` | Sales |
| `OrderCancelledIntegrationEvent` | `sales.order.cancelled.v1` | `sales.order` | Sales |
| `StockReservedIntegrationEvent` | `inventory.stock.reserved.v1` | `inventory.stock` | Inventory |
| `StockReleasedIntegrationEvent` | `inventory.stock.released.v1` | `inventory.stock` | Inventory |
| `StockReservationFailedIntegrationEvent` | `inventory.stock.reservation-failed.v1` | `inventory.stock` | Inventory |
| `InvoiceIssuedIntegrationEvent` | `billing.invoice.issued.v1` | `billing.invoice` | Billing |
| `InvoiceVoidedIntegrationEvent` | `billing.invoice.voided.v1` | `billing.invoice` | Billing |
| `PaymentCapturedIntegrationEvent` | `billing.payment.captured.v1` | `billing.payment` | Billing |
| `PaymentFailedIntegrationEvent` | `billing.payment.failed.v1` | `billing.invoice` | Billing |
| `FulfillmentCompletedIntegrationEvent` | `fulfillment.completed.v1` | `fulfillment.process` | Fulfillment |
| `FulfillmentFailedIntegrationEvent` | `fulfillment.failed.v1` | `fulfillment.process` | Fulfillment |

注意：Inventory 不再定义按 SKU 的领域事件；它对外只发布订单级集成事件 `StockReservedIntegrationEvent` / `StockReleasedIntegrationEvent` / `StockReservationFailedIntegrationEvent`，由库存应用服务直接构造。`StockReleasedIntegrationEvent` 只在确实释放了预占时写入，未预占的幂等补偿不会产生虚假事件。Billing 则保留四个领域事件，并在 `BillingApplicationService` 中全部翻译为对应的集成事件。

### 写入路径

以 Sales 下单为例：

1. `OrderApplicationService.place` 标注 `@Transactional`；
2. 加载订单，调用 `order.place(pricingPolicy, clock.instant())`；
3. 聚合内部 `registerEvent(new OrderPlaced(...))`；
4. `orderRepository.save(order)` 写 `sales_order` / `sales_order_line`；
5. `appendIntegrationEvents(order)` 从 `pullDomainEvents()` 取出领域事件；
6. `toIntegrationEvent(...)` 把 `OrderPlaced` 翻译为 `OrderPlacedIntegrationEvent`（行项目翻译为 `OrderLinePayload`）；
7. `outboxAppender.append(...)` 序列化并写 `outbox_event`。

第 4 步与第 7 步在同一个事务里，由 `@Transactional` 保证。`OutboxAppender` 和 `JdbcOutboxRepository` 都**没有**声明事务，这是有意的：它们必须服从调用方的事务边界。

```text
OrderApplicationService.place (@Transactional)
├── order.place(...)  -> 领域事件
├── orderRepository.save(...)
└── outboxAppender.append(...) -> INSERT outbox_event
     （同一事务提交）
```

### 发布路径

`OutboxRelay.relayOnce()`：

1. `repository.findUnpublished(50)`：按 `occurred_at, id` 取最多 50 条 `published_at IS NULL` 的事件；
2. 逐条调用 `OutboxPublisher.publishOne(event)`；
3. `OutboxPublisher.publishOne` 是 `@Transactional`：反序列化 → `IntegrationEventDispatcher.dispatch(event)` → `repository.markPublished(id)`；
4. 分发失败时异常向上抛出，`OutboxRelay` 记录日志并**中断本轮**；
5. 该条事件的事务回滚，`published_at` 仍为 `null`，下一轮会重试。

`SpringEventDispatcher` 把集成事件作为 Spring `ApplicationEvent` 发布，订阅者是带 `@EventListener` 的方法：

- `FulfillmentApplicationService.onOrderPlaced(OrderPlacedIntegrationEvent)`
- `OrderSummaryProjector.onOrderPlaced/onOrderConfirmed/onOrderCancelled(...)`

因为没有消息中间件，发布是**进程内同步分发**；但语义上仍按「至少一次」设计，消费者必须幂等。

### 消费幂等

`ProcessedEventStore.tryMarkProcessed(UUID eventId, String consumer)` 用 `(event_id, consumer)` 主键认领事件：

- 第一次插入成功返回 `true`，消费者继续处理；
- 已存在返回 `false`（或捕获主键冲突），消费者直接跳过。

两个实际消费者：

| consumer 字符串 | 位置 | 作用 |
|---|---|---|
| `fulfillment` | `FulfillmentSteps.startOrClaim` | 同一 `OrderPlaced` 只启动一次履约/只预占一次 |
| `order-summary` | `OrderSummaryProjector` | 同一事件只更新一次投影 |

此外还有**业务层幂等**：

- `StockItem.reserve` 对同一 `orderId` 重复调用直接返回，不重复扣减；
- `StockItem.release` 对不存在的预占无操作；
- `BillingApplicationService.issueAndCapture` 通过 `billing_invoice.order_id` 唯一约束，重复调用直接返回首次结果，不再调用支付网关；
- `SalesPortAdapter.cancel` 调用销售应用的幂等方法 `OrderApplicationService.cancelIfNotCancelled`，订单已 `CANCELLED` 时直接返回，避免补偿重放触发非法状态迁移。

### 读模型投影

`OrderSummaryProjector` 先认领事件再写 `order_summary_view`：

- `sales.order.placed.v1` → 插入摘要行（`status=PLACED`，总额、行数、更新时间）；
- `sales.order.confirmed.v1` → 原地更新为 `CONFIRMED`；
- `sales.order.cancelled.v1` → 原地更新为 `CANCELLED`。

投影与 outbox 发布处于 `OutboxPublisher.publishOne` 的同一事务中：如果投影 SQL 失败，分发与 `published_at` 一起回滚，事件会被重试。

`OrderSummaryRebuilder` 在应用启动时若投影表为空，会从写模型全量重建；它先用 `DELETE FROM order_summary_view`，再遍历 `OrderApplicationService.listAll()` 中 `placedAt != null` 的订单插入。重建使用 `TransactionTemplate`，避免自调用绕过事务。

### 调度器与确定性

`bootstrap/SchedulingConfig` 开启 `@EnableScheduling`，`bootstrap/OutboxScheduler` 默认每 200ms 调用一次
`OutboxRelay.relayOnce()`。因此普通 REST 启动后，下单、履约与投影会自动推进。

为了保持测试和演示的确定性：

- `application-test.yml` 与 `application-demo.yml` 设置 `orders.outbox.scheduler.enabled=false`；
- 测试显式注入 `OutboxRelay` 并调用 `relayOnce()`；
- `DemoRunner.relayUntilIdle()` 显式循环 `relayOnce()`，直到没有待发布事件。

调度间隔由 `orders.outbox.relay-interval` 覆盖，默认 200ms。这是教学项目的最小实现；真实系统还需要线程池、失败退避、死信与监控。

### 超时与一致性

支付超时在 `ExternalPaymentGatewayAdapter` 中翻译为 `TimedOut`；`BillingApplicationService` 把 `TimedOut` 当失败处理，`Payment` 置为 `FAILED`、原因 `TIMEOUT`，`Invoice` 作废，并发布 `billing.payment.failed.v1`。随后 Fulfillment 走补偿。

真实系统不能简单把超时当失败：支付可能已在网关成功，只是响应丢失。需要「结果未知」状态、查询网关、对账或人工处理。本项目刻意不实现，并在 [ADR-0005](adr/0005-no-event-sourcing-in-main-flow.md) 与设计文档中说明。

## 反例与常见误解

### 反例 1：保存后直接发布事件

```java
orderRepository.save(order);          // 事务提交
eventPublisher.publish(orderPlaced);  // 如果这里失败，事件永久丢失
```

如果发布不在同一事务内，数据库与事件会分叉。Outbox 把「写事件」变成数据库写入，解决了这个问题。

### 反例 2：先标记已发布，再分发

```java
repository.markPublished(id);
dispatcher.dispatch(event);  // 分发失败时，事件再也不会重试
```

本项目的 `OutboxPublisher` 顺序相反：先 `dispatch`，成功后才 `markPublished`；整个方法同一事务。

### 反例 3：把领域事件直接作为集成事件

`OrderPlaced` 携带 `List<OrderLine>`。如果直接把它发给 Fulfillment，Fulfillment 就依赖 Sales 的领域模型；Sales 重构订单行会破坏 Fulfillment。本项目为集成事件单独定义 `OrderLinePayload`，并在 `OrderPlacedIntegrationEventContractTest` 中验证跨上下文可读取。

### 误解 4：事件是「立即一致」的

`OrderApplicationService.place` 返回时，订单已经是 `PLACED`，但履约流程可能还没有启动，读模型也可能还没有这一行。只有 relay 发布并消费后，下游才最终一致。测试里因此要显式调用 `relay.relayOnce()`。

### 误解 5：Outbox 等于消息队列

Outbox 只是数据库表和发布流程，本项目没有 broker、没有独立消费者进程、没有死信队列。它的价值是**原子性与可重试**，不是吞吐量。

### 误解 6：重复事件一定会被业务层挡住

业务层幂等（如库存预占）只覆盖部分场景；通用的重复投递防护依赖 `ProcessedEventStore`。两者都需要，不能只依赖其中一个。
