# 04 · 战术模式：概念 → 类 → 路径

## 概念

战术设计关注**一个限界上下文内部**如何建模：

- **聚合（Aggregate）**：一组必须保持一致的对象，聚合根是唯一入口；一次事务只修改一个聚合。
- **实体（Entity）**：有身份、生命周期可变的对象。
- **值对象（Value Object）**：由属性值定义、不可变、可替换。
- **领域事件（Domain Event）**：聚合内已经发生的事实，用过去式命名。
- **领域服务（Domain Service）**：不属于任何实体/值对象、但又表达领域规则的纯操作。
- **仓储（Repository）**：以聚合为单位提供持久化，接口在领域层，实现在基础设施层。
- **工厂（Factory）**：封装复杂创建逻辑。注意：**不要为了模式而造工厂**；如果创建只是 `new`，静态工厂方法就够了。

本项目刻意避免「一行 `new` 的工厂类」，创建逻辑通过静态工厂/`rehydrate` 表达。

## 本项目在哪

### 总表

| 概念 | 类 / 接口 | 文件路径 | 说明 |
|---|---|---|---|
| 聚合根基类 | `AggregateRoot` | `src/main/java/com/example/orders/sharedkernel/AggregateRoot.java` | 收集领域事件 + 承载乐观锁版本；`version/assignVersion` 是务实的持久化妥协 |
| 聚合根 | `Order` | `src/main/java/com/example/orders/sales/domain/Order.java` | 订单行、状态机、金额、折扣、领域事件 |
| 聚合根 | `StockItem` | `src/main/java/com/example/orders/inventory/domain/StockItem.java` | 在库、预占、防超卖、释放幂等 |
| 聚合根 | `Invoice` | `src/main/java/com/example/orders/billing/domain/Invoice.java` | 发票状态机 `ISSUED → PAID / VOIDED` |
| 聚合根 | `Payment` | `src/main/java/com/example/orders/billing/domain/Payment.java` | 支付状态机 `PENDING → CAPTURED / FAILED` |
| 聚合根 | `FulfillmentProcess` | `src/main/java/com/example/orders/fulfillment/domain/FulfillmentProcess.java` | Saga 流程状态机 |
| 实体 | `OrderLine` | `src/main/java/com/example/orders/sales/domain/OrderLine.java` | 订单内实体，不可变 record；`subtotal()` |
| 实体 | `Reservation` | `src/main/java/com/example/orders/inventory/domain/Reservation.java` | 库存聚合内实体：`orderId + quantity` |
| 值对象 | `Money` | `src/main/java/com/example/orders/sharedkernel/Money.java` | `BigDecimal` scale 2 HALF_UP + `Currency`，跨币种运算抛异常 |
| 值对象 | `Currency` | `src/main/java/com/example/orders/sharedkernel/Currency.java` | ISO 4217 三位大写代码 |
| 值对象 | `Sku` | `src/main/java/com/example/orders/sharedkernel/Sku.java` | 去空格、转大写、非空 |
| 值对象 | `Quantity` | `src/main/java/com/example/orders/sharedkernel/Quantity.java` | 1..999 |
| 值对象 | `OrderId` / `CustomerId` | `src/main/java/com/example/orders/sharedkernel/OrderId.java` / `CustomerId.java` | 跨上下文共享的标识 |
| 值对象 | `LineId` / `InvoiceId` / `PaymentId` / `ProcessId` | 各自上下文的 `domain` 包 | 上下文内部的标识 |
| 状态枚举 | `OrderStatus` | `src/main/java/com/example/orders/sales/domain/OrderStatus.java` | `DRAFT / PLACED / CONFIRMED / CANCELLED` |
| 状态枚举 | `InvoiceStatus` / `PaymentStatus` | `src/main/java/com/example/orders/billing/domain/` | 发票与支付状态 |
| 状态枚举 | `FulfillmentStatus` | `src/main/java/com/example/orders/fulfillment/domain/FulfillmentStatus.java` | `STARTED / STOCK_RESERVED / PAID / COMPLETED / COMPENSATING / CANCELLED / FAILED` |
| 领域事件 | `OrderPlaced` | `src/main/java/com/example/orders/sales/domain/OrderPlaced.java` | `sales.order.placed`，携带行快照与金额 |
| 领域事件 | `OrderConfirmed` / `OrderCancelled` | `src/main/java/com/example/orders/sales/domain/` | `sales.order.confirmed` / `sales.order.cancelled` |
| 领域事件 | `OrderLineAdded` / `OrderLineRemoved` | `src/main/java/com/example/orders/sales/domain/` | `sales.order.line-added` / `sales.order.line-removed` |
| 领域事件 | `StockReserved` / `StockReleased` / `StockReservationFailed` | `src/main/java/com/example/orders/inventory/domain/` | 领域事件不带版本；失败事件由应用层构造 |
| 领域事件 | `InvoiceIssued` / `InvoiceVoided` / `PaymentCaptured` / `PaymentFailed` | `src/main/java/com/example/orders/billing/domain/` | Billing 内部事实 |
| 领域事件基接口 | `DomainEvent` | `src/main/java/com/example/orders/sharedkernel/DomainEvent.java` | `eventId/occurredAt/eventType/aggregateId` |
| 领域服务 | `PricingPolicy` | `src/main/java/com/example/orders/sales/domain/PricingPolicy.java` | 折扣策略接口：`Money discountFor(Order)` |
| 领域服务实现 | `TieredPricingPolicy` | `src/main/java/com/example/orders/sales/domain/TieredPricingPolicy.java` | 总件数 ≥ 20 打 9 折，≥ 10 打 95 折 |
| 仓储接口 | `OrderRepository` | `src/main/java/com/example/orders/sales/domain/OrderRepository.java` | 聚合级持久化端口 |
| 仓储接口 | `StockItemRepository` | `src/main/java/com/example/orders/inventory/domain/StockItemRepository.java` | 含 `findByReservationOrderId` |
| 仓储接口 | `InvoiceRepository` / `PaymentRepository` | `src/main/java/com/example/orders/billing/domain/` | 按订单/发票查询 |
| 仓储接口 | `FulfillmentProcessRepository` | `src/main/java/com/example/orders/fulfillment/domain/FulfillmentProcessRepository.java` | 按订单或流程 ID 查询 |
| 仓储实现 | `JdbcOrderRepository` | `src/main/java/com/example/orders/sales/infrastructure/JdbcOrderRepository.java` | 手写领域对象 ↔ `OrderRecord` 映射 |
| 仓储实现 | `JdbcStockItemRepository` | `src/main/java/com/example/orders/inventory/infrastructure/JdbcStockItemRepository.java` | 预占明细随聚合整体读写 |
| 仓储实现 | `JdbcInvoiceRepository` / `JdbcPaymentRepository` | `src/main/java/com/example/orders/billing/infrastructure/` | 分别映射发票与支付 |
| 仓储实现 | `JdbcFulfillmentProcessRepository` | `src/main/java/com/example/orders/fulfillment/infrastructure/JdbcFulfillmentProcessRepository.java` | 流程状态持久化 |
| 持久化记录 | `OrderRecord` / `OrderLineRecord` 等 | 各上下文 `infrastructure/*Record.java` | 只用于表映射，不承载领域行为 |
| 应用服务 | `OrderApplicationService` | `src/main/java/com/example/orders/sales/application/OrderApplicationService.java` | 用例编排 + 事务 + outbox 写入 |
| 应用服务 | `InventoryApplicationService` | `src/main/java/com/example/orders/inventory/application/InventoryApplicationService.java` | 多 SKU 全有或全无预占 |
| 应用服务 | `BillingApplicationService` | `src/main/java/com/example/orders/billing/application/BillingApplicationService.java` | 开票 + 扣款 + 幂等 |
| 应用服务 | `FulfillmentApplicationService` | `src/main/java/com/example/orders/fulfillment/application/FulfillmentApplicationService.java` | Saga 编排（无事务） |
| 步骤执行器 | `FulfillmentSteps` | `src/main/java/com/example/orders/fulfillment/application/FulfillmentSteps.java` | 每步 `REQUIRES_NEW` 独立事务 |
| 端口 | `InventoryPort` / `BillingPort` / `SalesPort` | `src/main/java/com/example/orders/fulfillment/application/port/` | Fulfillment 自己定义的跨上下文接口 |
| 适配器 | `InventoryPortAdapter` / `BillingPortAdapter` / `SalesPortAdapter` | `src/main/java/com/example/orders/fulfillment/infrastructure/` | 调对方应用服务并翻译结果类型 |
| 防腐层 | `ExternalPaymentGatewayAdapter` | `src/main/java/com/example/orders/billing/infrastructure/ExternalPaymentGatewayAdapter.java` | 外部「分 + 状态码」→ 本地 `CaptureResult` |
| 防腐层端口 | `PaymentGateway` | `src/main/java/com/example/orders/billing/domain/PaymentGateway.java` | 领域层定义的支付端口 |
| 集成事件契约 | `IntegrationEvent` | `src/main/java/com/example/orders/sharedkernel/outbox/IntegrationEvent.java` | `eventId/eventType/aggregateType/aggregateId/occurredAt` |
| 集成事件 | `OrderPlacedIntegrationEvent` 等 10 个 | `src/main/java/com/example/orders/sharedkernel/outbox/` | 类型字符串带 `.v1` |
| 发件箱 | `OutboxAppender` | `src/main/java/com/example/orders/sharedkernel/outbox/OutboxAppender.java` | 序列化 + 追加，不开事务 |
| 发件箱仓储 | `JdbcOutboxRepository` | `src/main/java/com/example/orders/sharedkernel/outbox/JdbcOutboxRepository.java` | `outbox_event` 表 |
| 发布器 | `OutboxPublisher` | `src/main/java/com/example/orders/sharedkernel/outbox/OutboxPublisher.java` | 单条事件独立事务：反序列化 → 分发 → 标记已发布 |
| 中继 | `OutboxRelay` | `src/main/java/com/example/orders/sharedkernel/outbox/OutboxRelay.java` | 每轮最多 50 条，失败中断本轮 |
| 消费幂等 | `ProcessedEventStore` / `JdbcProcessedEventStore` | `src/main/java/com/example/orders/sharedkernel/outbox/` | `tryMarkProcessed(eventId, consumer)` |
| 读模型投影 | `OrderSummaryProjector` | `src/main/java/com/example/orders/projections/OrderSummaryProjector.java` | 监听集成事件，写 `order_summary_view` |
| 读模型重建 | `OrderSummaryRebuilder` | `src/main/java/com/example/orders/projections/OrderSummaryRebuilder.java` | 启动时视图为空则从写模型重建 |
| 事件溯源示例 | `Account` / `EventStore` / `InMemoryEventStore` | `src/main/java/com/example/orders/examples/eventsourcing/` | 不接 Spring，独立示例 |
| 领域异常 | `DomainException` | `src/main/java/com/example/orders/sharedkernel/DomainException.java` | 所有领域规则异常的根 |
| 状态异常 | `InvalidOrderStateException` / `InvalidPaymentStateException` / `InvalidFulfillmentStateException` | 各上下文 `domain` 包 | 非法状态迁移，API 映射为 409 |
| 未找到异常 | `NotFoundException` | `src/main/java/com/example/orders/sharedkernel/NotFoundException.java` | API 映射为 404 |

### 静态工厂与重建

本项目没有独立的 `OrderFactory`、`StockItemFactory` 类，因为创建逻辑不足以复杂到需要工厂：

- `Order.draft(...)`：创建草稿订单；
- `Order.rehydrate(...)`：从持久化状态重建，**不产生事件**；
- `StockItem.create(...)` / `StockItem.rehydrate(...)`；
- `Invoice.issue(...)` / `Invoice.rehydrate(...)`；
- `Payment.request(...)` / `Payment.rehydrate(...)`；
- `FulfillmentProcess.start(...)` / `FulfillmentProcess.rehydrate(...)`。

`rehydrate` 与业务创建方法分开，是为了避免「从数据库加载订单」时误发 `OrderPlaced` 之类的事件。这是本项目一个重要的实现细节。

## 反例与常见误解

### 反例 1：贫血模型

如果把 `Order` 写成只有 getter/setter，把「至少一行」「币种一致」「只能取消 DRAFT/PLACED」放到 `OrderApplicationService`，那么任何新用例都可能绕过不变量。本项目的 `Order.addLine/place/confirm/cancel` 自己维护规则，应用服务只负责加载、保存和写 outbox。

### 反例 2：仓储返回数据库记录

`OrderRepository` 的返回类型是 `Order`，不是 `OrderRecord`。`OrderRecord` 只存在于 `sales/infrastructure`，领域层完全不知道它的存在。这样领域测试不需要 Spring 或数据库。

### 反例 3：为了模式而造工厂

「工厂方法模式」不是 DDD 的必选项。本项目的创建逻辑用静态工厂方法就够；额外造一个只有 `return new Order(...)` 的工厂类，会增加一层无意义间接。

### 反例 4：把 `@Version` 当成领域概念

`AggregateRoot.version()` 暴露在共享内核里，是为了让 Spring Data JDBC 做乐观锁。它不参与任何业务不变量。设计文档明确把这称为「有意的务实妥协」，不要把它解释成领域规则。
