# 08 · 场景走读：两条 Demo 路径

## 概念

读 Saga 最好的方式不是看类图，而是跟着**事件**走一遍：谁在什么事务里写了什么事实，谁消费了它，状态如何迁移，失败时哪些动作被补偿。本篇按 `demo` profile 的真实输出走读。

Demo 数据在 `src/main/java/com/example/orders/bootstrap/DemoData.java`：

- 客户：`11111111-1111-1111-1111-111111111111`
- 成功路径 SKU：`DEMO-KEYBOARD`，单价 199.00 CNY，数量 2
- 失败路径 SKU：`DEMO-MOUSE`，单价 89.00 CNY，数量 3
- 运行演示前为两个 SKU 各补货 20 件（`DemoRunner.runScenarios` 在两个场景之前分别调用一次 `ensureStock`）

Demo 通过 `SimulatedLegacyPaymentClient.setStatusCode("51")` 让失败路径的支付被拒绝（`51` = 余额不足），结束后恢复 `"00"`。

## 本项目在哪

| 内容 | 代码位置 |
|---|---|
| Demo 入口与两条场景 | `src/main/java/com/example/orders/bootstrap/DemoRunner.java` |
| Demo 固定数据 | `src/main/java/com/example/orders/bootstrap/DemoData.java` |
| Demo 端到端测试 | `src/test/java/com/example/orders/bootstrap/DemoRunnerTest.java` |
| 成功路径编排 | `src/main/java/com/example/orders/fulfillment/application/FulfillmentApplicationService.java`、`FulfillmentSteps.java` |
| 成功路径测试 | `src/test/java/com/example/orders/fulfillment/FulfillmentHappyPathTest.java` |
| 支付失败补偿测试 | `src/test/java/com/example/orders/fulfillment/FulfillmentCompensationTest.java` |
| 库存不足路径 | `FulfillmentApplicationService.onOrderPlaced` 中 `StockShortage` 分支 + `FulfillmentSteps.markFailed` / `releaseStock` / `cancelOrder` / `recordFailure` |
| 支付网关模拟 | `src/main/java/com/example/orders/billing/infrastructure/SimulatedLegacyPaymentClient.java` |

## Success path 成功路径

### 事件时间线（Demo 实际输出）

```text
sales.order.placed.v1
inventory.stock.reserved.v1
billing.payment.captured.v1
sales.order.confirmed.v1
fulfillment.completed.v1
```

### 逐步走读

| 步骤 | 触发动作 | 事务 / 代码 | 写入的事实 | 状态变化 |
|---|---|---|---|---|
| 1 | `POST /api/orders`（Demo 直接调应用服务） | `OrderApplicationService.create` `@Transactional` | `sales_order` + `sales_order_line` | `Order` 为 `DRAFT` |
| 2 | `POST /api/orders/{id}/place` | `OrderApplicationService.place` `@Transactional` | 保存订单；`OrderPlaced` 领域事件 → `sales.order.placed.v1` 写入 `outbox_event` | `DRAFT → PLACED` |
| 3 | `OutboxRelay.relayOnce()` 发布 `sales.order.placed.v1` | `OutboxPublisher.publishOne` `@Transactional` | `published_at` 更新 | — |
| 4 | `FulfillmentApplicationService.onOrderPlaced` 监听 | `FulfillmentSteps.startOrClaim` `REQUIRES_NEW` | `processed_event(consumer=fulfillment)`；`fulfillment_process` | `FulfillmentProcess` 为 `STARTED` |
| 5 | 预占库存 | `FulfillmentSteps.reserve` `REQUIRES_NEW` → `InventoryReservationFacade` → `InventoryApplicationService.reserve` | `inventory_reservation`；`inventory.stock.reserved.v1` | `StockItem.reserved = 2`；流程 `STOCK_RESERVED` |
| 6 | 开票并扣款 | `FulfillmentSteps.pay` `REQUIRES_NEW` → `BillingPortAdapter` → `BillingApplicationService.issueAndCapture` | `billing_invoice(PAID)`、`billing_payment(CAPTURED)`；`billing.payment.captured.v1` | 流程 `PAID` |
| 7 | 确认订单并完成 | `FulfillmentSteps.confirmAndComplete` `REQUIRES_NEW` → `SalesPortAdapter.confirm` → `OrderApplicationService.confirmOrder` | 订单 `CONFIRMED`；`sales.order.confirmed.v1`；`fulfillment.completed.v1` | `Order: PLACED → CONFIRMED`；流程 `PAID → COMPLETED` |

### 关键点

- 第 2 步的 `sales.order.placed.v1` 与订单保存**同一个事务**；第 6 步的 `billing.payment.captured.v1` 与发票/支付写入同一个事务。
- Fulfillment 的每个步骤都是 `REQUIRES_NEW`，因此第 4、5、6、7 步各自提交。即使第 6 步失败，第 5 步的库存预占已经提交，后续必须补偿。
- `FulfillmentCompletedIntegrationEvent` 在 `confirmAndComplete` 中与订单确认同一步写出；它本身没有消费者，但构成了完整的跨上下文事实。
- Demo 的 `relayUntilIdle()` 会循环调用 `relayOnce()`，把 Saga 过程中新写入的集成事件也发布出来；这就是为什么时间线里能看到后续所有事件。

## Failure path 支付失败补偿路径

### 事件时间线（Demo 实际输出）

```text
sales.order.placed.v1
inventory.stock.reserved.v1
billing.payment.failed.v1
inventory.stock.released.v1
sales.order.cancelled.v1
fulfillment.failed.v1
```

### 逐步走读

| 步骤 | 触发动作 | 事务 / 代码 | 写入的事实 | 状态变化 |
|---|---|---|---|---|
| 1 | 下单 | 同成功路径 | `sales.order.placed.v1` | `DRAFT → PLACED` |
| 2 | 启动履约 + 预占 | 同成功路径 | `inventory.stock.reserved.v1` | 流程 `STOCK_RESERVED`；库存已预占 |
| 3 | 扣款被拒绝 | `FulfillmentSteps.pay` → `BillingApplicationService.issueAndCapture` | `Payment(FAILED)`、`Invoice(VOIDED)`；`billing.payment.failed.v1`（reason=`余额不足`） | 流程仍为 `STOCK_RESERVED` |
| 4 | 进入补偿 | `FulfillmentApplicationService.compensate` → `FulfillmentSteps.markCompensating` `REQUIRES_NEW` | 保存流程 | `STOCK_RESERVED → COMPENSATING` |
| 5 | 释放库存 | `FulfillmentSteps.releaseStock` `REQUIRES_NEW` → `InventoryApplicationService.release` | 删除 `inventory_reservation`；`inventory.stock.released.v1` | 库存可用量恢复 |
| 6 | 取消订单 | `FulfillmentSteps.cancelOrder` `REQUIRES_NEW` → `SalesPortAdapter.cancel` → `OrderApplicationService.cancel` | 订单 `CANCELLED`；`sales.order.cancelled.v1` | `Order: PLACED → CANCELLED` |
| 7 | 收尾 | `FulfillmentSteps.finalizeCancelled` `REQUIRES_NEW` | 流程 `CANCELLED`；`fulfillment.failed.v1`（合并次要失败原因） | `COMPENSATING → CANCELLED` |

### 关键点

- 补偿动作**不在一个事务里**：第 4、5、6、7 步分别提交。任何一个动作失败，其他动作仍会继续尝试；失败原因会记录到日志并合并进最终 `FulfillmentFailedIntegrationEvent` 的 reason。
- 第 7 步的 `finalizeCancelled` 会重新从数据库读取流程状态，因此即使第 4 步 `markCompensating` 失败、流程仍是 `STOCK_RESERVED`/`PAID`，它也会先 `startCompensation` 再 `markCancelled`，实现自愈。
- 支付失败时 Billing 已作废发票，但本项目**没有真实退款**；库存是预占而非已出库，所以释放即可。
- `FulfillmentFailedIntegrationEvent` 的 `reason` 可能是主失败原因，也可能是 `主原因（补偿异常: ...）` 的合并文本。

## Stock shortage path 库存不足

Demo 不展示这条路径，但 `FulfillmentCompensationTest.stockShortageCancelsOrder` 固化了它。事件时间线是：

```text
sales.order.placed.v1
inventory.stock.reservation-failed.v1
sales.order.cancelled.v1
fulfillment.failed.v1
```

走读：

1. Sales 下单，发布 `sales.order.placed.v1`；
2. Fulfillment 启动流程，调用库存预占；
3. `InventoryApplicationService.reserve` 发现可用量不足，**不修改任何库存**，写 `inventory.stock.reservation-failed.v1` 并返回 `StockShortage`；
4. `FulfillmentApplicationService` 依次调 `FulfillmentSteps.markFailed`、`releaseStock`、`cancelOrder`、`recordFailure`（各自独立事务、尽力而为）：流程 `STARTED → FAILED`，调用 Sales 取消订单（写 `sales.order.cancelled.v1`），写 `fulfillment.failed.v1`。

注意这里不会出现 `inventory.stock.released.v1`，因为根本没有预占成功。

## 重复投递会发生什么

### 相同 eventId 的重复事件

`FulfillmentSteps.startOrClaim` 先调用 `ProcessedEventStore.tryMarkProcessed(eventId, "fulfillment")`。同一 `eventId` 第二次到达时返回 `false`，`onOrderPlaced` 直接返回，不会重复预占。

### 复制出不同 eventId、但业务内容相同的事件

`FulfillmentCompensationTest.duplicateOrderPlacedEventDoesNotReserveTwice` 模拟了这种更恶劣的情况：复制 `outbox_event` 行但生成新的 `eventId`。此时幂等表挡不住，但业务层仍然安全：

- `StockItem.reserve` 对同一 `orderId` 幂等，不会重复扣减；
- Billing 按 `order_id` 返回已有发票结果，不会重复扣款；
- 流程已到 `COMPLETED`，后续步骤非法迁移会被捕获，`routeFailure` 看到终态后不做处理。

测试最终断言：库存只预占 2、只调用网关一次、流程 `COMPLETED`、订单 `CONFIRMED`。

## 超时路径（与支付失败同构）

把 `SimulatedLegacyPaymentClient` 的状态码设为 `"91"`（或在测试中用 `FakePaymentGateway.enqueue(new TimedOut())`），适配器返回 `TimedOut`；`BillingApplicationService` 将其视为失败：

- `Payment` → `FAILED`，`failureReason = "TIMEOUT"`；
- `Invoice` → `VOIDED`；
- 发布 `billing.payment.failed.v1`，reason 为 `TIMEOUT`；
- 后续补偿与支付被拒绝完全相同。

**这是刻意简化。** 真实系统中超时代表「结果未知」，不能直接当失败；需要查询网关或对账。详见 [06 事件与一致性](06-events-and-consistency.md)。

## 反例与常见误解

### 误解 1：`place` 返回后履约就完成了

`place` 只保证订单变成 `PLACED` 且 `OrderPlaced` 已进入 outbox。履约需要 relay 发布事件后才开始。测试中必须显式调用 `relay.relayOnce()`，Demo 中由 `relayUntilIdle()` 驱动。

### 误解 2：支付失败后数据库事务会整体回滚

不会。库存预占已经在第 2 步提交；支付失败发生在第 3 步的另一个事务中。系统只能通过**补偿**（释放库存、取消订单）回到业务上可接受的状态，而不是数据库回滚。这是 Saga 与本地事务的本质区别。

### 误解 3：补偿一定全部成功

补偿是尽力而为：每个动作独立事务，失败会记录并继续。`finalizeCancelled` 只保证流程最终落到 `CANCELLED` 并写出失败事件；真实系统还需要告警、人工对账与重试队列。

### 误解 4：库存不足与支付失败走同一条补偿

库存不足时没有预占成功，因此走 `FAILED` 而不是 `COMPENSATING`，也不释放库存；支付失败时预占已成功，必须先释放再取消。两条路径的终态分别是 `FAILED` 与 `CANCELLED`。
