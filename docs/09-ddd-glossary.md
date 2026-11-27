# 09 · DDD 术语速查

## 概念

本篇是术语参考，不是教程。每条术语给出简短定义和本项目中的具体落点，便于读代码时快速定位。更完整的上下文见各专题文档。

## 本项目在哪

下表的「本项目在哪」列就是索引：从术语可以直接跳到对应的类、接口或表。若一个术语对应多个位置，优先给出最能说明该概念的落点。

## 术语表

| 术语 | 英文 | 定义 | 本项目在哪 |
|---|---|---|---|
| 限界上下文 | Bounded Context | 一个模型保持一致性的边界；边界内术语有唯一含义 | `sales`、`inventory`、`billing`、`fulfillment` 四个顶层包 |
| 子域 | Subdomain | 问题空间中的一块业务领域 | Sales（核心）、Inventory/Billing/Fulfillment（支撑），见 [02 子域](02-subdomains.md) |
| 核心域 | Core Domain | 业务差异化与竞争力所在 | Sales，`sales/domain/Order` |
| 支撑域 | Supporting Subdomain | 为核心域服务、需要定制但非竞争壁垒 | Inventory、Billing、Fulfillment |
| 通用域 | Generic Subdomain | 业界成熟、通常购买/复用的能力 | 本项目未实现；外部支付网关是外部系统，不是通用子域 |
| 统一语言 | Ubiquitous Language | 团队与代码共用的业务术语 | 见 [01 统一语言](01-ubiquitous-language.md) |
| 上下文映射 | Context Map | 上下文之间的协作关系图 | 见 [03 上下文映射](03-context-map.md) |
| 客户-供应商 | Customer-Supplier | 下游提需求，上游定契约 | Fulfillment → Inventory/Billing/Sales 的端口适配器 |
| 防腐层 | Anti-Corruption Layer (ACL) | 隔离并翻译外部/上游模型 | `billing/infrastructure/ExternalPaymentGatewayAdapter` |
| 共享内核 | Shared Kernel | 多上下文共同维护的一小块模型 | `sharedkernel/Money`、`Currency`、`Sku`、`Quantity`、`OrderId`、`CustomerId` |
| 发布语言 | Published Language | 稳定的跨上下文事件/消息契约 | `sharedkernel/outbox/*IntegrationEvent`，类型带 `.v1` |
| 分离方式 | Separate Ways | 不强行建立关系的上下文 | Inventory 与 Billing 互不调用；Sales 与 Billing 无直接依赖 |
| 聚合 | Aggregate | 一组必须保持一致的对象，通过聚合根访问 | `Order`、`StockItem`、`Invoice`、`Payment`、`FulfillmentProcess` |
| 聚合根 | Aggregate Root | 聚合的唯一入口，维护不变量 | 上述五个聚合根类 |
| 实体 | Entity | 有身份、生命周期可变的对象 | `OrderLine`、`Reservation` |
| 值对象 | Value Object | 由值定义、不可变、可替换 | `Money`、`Currency`、`Sku`、`Quantity`、各 `*Id` |
| 不变量 | Invariant | 聚合在任何时刻都必须满足的规则 | `Order` 的币种一致、至少一行、折扣边界；`StockItem.available >= 0` |
| 状态机 | State Machine | 有明确状态与合法迁移的行为模型 | `Order.place/confirm/cancel`、`Invoice.markPaid/voidIt`、`FulfillmentProcess` 的 `switch` 迁移 |
| 领域事件 | Domain Event | 聚合内已经发生的事实，过去式命名 | `OrderPlaced`、`InvoiceIssued`、`PaymentCaptured` 等 |
| 集成事件 | Integration Event | 跨上下文、版本化的稳定契约 | `sharedkernel/outbox/*IntegrationEvent` |
| 事件版本 | Event Versioning | 用类型字符串版本管理契约演进 | `.v1` 后缀，如 `sales.order.placed.v1` |
| 领域服务 | Domain Service | 不属于实体/值对象的领域操作 | `PricingPolicy` / `TieredPricingPolicy` |
| 应用服务 | Application Service | 编排用例、事务、DTO 转换、端口调用 | `OrderApplicationService`、`InventoryApplicationService`、`BillingApplicationService` |
| 仓储 | Repository | 以聚合为单位的持久化端口 | `OrderRepository` 等 domain 接口 + `Jdbc*Repository` |
| 工厂 | Factory | 封装复杂创建逻辑 | 本项目用静态工厂/`rehydrate`，没有独立工厂类 |
| 重建 | Rehydration | 从持久化状态恢复聚合，不产生新事件 | `Order.rehydrate`、`StockItem.rehydrate`、`Invoice.rehydrate`、`FulfillmentProcess.rehydrate` |
| 领域异常 | Domain Exception | 领域规则被违反时抛出 | `DomainException` 及其子类 `Invalid*StateException`、`InsufficientStockException` |
| 乐观锁 | Optimistic Locking | 用版本号检测并发写冲突 | 聚合 `@Version`；`AggregateRoot.version/assignVersion` |
| 端口与适配器 | Ports & Adapters | 领域定义端口，基础设施实现适配器 | `PaymentGateway` + `ExternalPaymentGatewayAdapter`；`InventoryPort` + `InventoryPortAdapter` |
| 流程管理器 | Process Manager | 协调跨多个聚合/上下文的长期流程 | `FulfillmentApplicationService` |
| Saga | Saga | 用一系列本地事务 + 补偿实现跨上下文流程 | Fulfillment 的 `FulfillmentSteps` 与 `compensate(...)` |
| 补偿 | Compensation | 失败后反向执行的动作 | 释放库存、取消订单；`FulfillmentSteps.releaseStock/cancelOrder` |
| 编排式 Saga | Orchestration Saga | 由一个中心协调者驱动步骤 | `FulfillmentApplicationService` 调用 `FulfillmentSteps` |
| 事务性 Outbox | Transactional Outbox | 业务写入与待发布事件同事务落库 | `OutboxAppender`、`JdbcOutboxRepository`、`outbox_event` |
| 中继 | Relay | 轮询 outbox 并发布未发布事件 | `OutboxRelay.relayOnce()` |
| 至少一次投递 | At-least-once Delivery | 事件可能重复，消费者必须幂等 | `OutboxPublisher` 失败回滚；`ProcessedEventStore` |
| 消费幂等 | Idempotent Consumer | 同一事件重复消费不改变结果 | `ProcessedEventStore.tryMarkProcessed`；库存预占/释放、Billing 按订单幂等 |
| 最终一致性 | Eventual Consistency | 下游在一段时间后达到一致，而非瞬时一致 | `order_summary_view` 由 relay 后的投影维护 |
| CQRS | Command Query Responsibility Segregation | 写模型与读模型分离 | `sales` 写模型 vs `projections/OrderSummaryQuery` 读模型 |
| 读模型 / 投影 | Read Model / Projection | 为查询优化的数据视图 | `order_summary_view`、`OrderSummaryProjector` |
| 投影重建 | Projection Rebuild | 从写模型或事件重建读模型 | `OrderSummaryRebuilder`（启动时视图为空则重建） |
| 事件溯源 | Event Sourcing | 以事件序列作为唯一事实来源，重放得到状态 | `examples/eventsourcing/Account`、`InMemoryEventStore` |
| 事件存储 | Event Store | 按流追加/读取事件的存储 | `examples/eventsourcing/EventStore`、`InMemoryEventStore` |
| 流版本 | Stream Version | 事件流的长度，用于乐观并发 | `EventStore.append` 的 `expectedVersion`；`StoredEvent.version` |
| 模块化单体 | Modular Monolith | 单部署单元内按业务模块/上下文分包 | 本项目整体结构，见 [ADR-0001](adr/0001-modular-monolith.md) |
| 问题空间 / 解决方案空间 | Problem / Solution Space | 业务问题 vs 代码实现 | 子域属于问题空间；限界上下文属于解决方案空间 |

## 几个容易混淆的术语

### 子域 ≠ 限界上下文

子域是业务问题的划分；限界上下文是模型边界。教学项目里两者恰好一一对应（Sales/Inventory/Billing/Fulfillment），但这不是必然规律。一个子域可以由多个上下文实现，一个上下文也可以覆盖多个子域。

### 领域事件 ≠ 集成事件

领域事件是上下文内部事实，载荷可以使用领域对象；集成事件是跨上下文契约，载荷必须稳定、版本化。本项目把它们分成两套类，由应用层翻译。

### Saga ≠ 分布式事务

Saga 不保证原子性，只保证「最终通过补偿达到可接受状态」。Fulfillment 的补偿动作分属不同事务，可能部分成功；这不是 bug，而是 Saga 的固有语义。

### Outbox ≠ 消息队列

Outbox 解决「数据库写入与事件发布原子性」；消息队列解决「传输、削峰、解耦部署」。本项目只有前者，发布仍是进程内 Spring 事件。

### 事件溯源 ≠ 事件驱动

事件驱动可以只发布事件、仍然保存当前状态；事件溯源则把事件序列当作唯一事实来源。本项目的**主流程是事件驱动 + 状态存储**，事件溯源只在 `examples/eventsourcing` 独立演示。

## 反例与常见误解

### 误解 1：术语越多越 DDD

DDD 不是术语堆砌。本项目的 `Order` 没有 `OrderFactory`、`OrderDomainService`、`OrderPolicy` 等一层层包装；只有在规则确实不属于某个对象时才引入领域服务（如 `PricingPolicy`）。

### 误解 2：共享内核越大越好

共享内核只放真正稳定、且多个上下文必须一致的值类型。`Money` 进共享内核是因为 Sales 与 Billing 必须对金额语义一致；订单状态、发票状态等则各自保留。代价见 [ADR-0003](adr/0003-shared-kernel-money.md)。

### 误解 3：聚合越大越好

一个聚合一次事务只改一个。`Order` 不包含 `StockItem` 或 `Invoice`；Fulfillment 也不把订单、库存、发票塞进一个聚合。跨聚合的一致性由 Saga 与事件解决。

### 误解 4：读模型可以反过来做业务决策

`OrderSummary` 是投影，可能滞后、可能重建；业务规则只读写模型的聚合。`OrderController` 的列表查询走读模型，但下单、取消、确认全部走 `OrderApplicationService` 与 `Order` 聚合。
