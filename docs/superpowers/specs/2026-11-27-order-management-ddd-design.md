# 订单管理系统 · DDD 教学项目 设计文档

> 状态：已批准
> 日期：2026-11-27
> 读者：实现者。读完这份文档应当可以直接拆计划、写代码，不需要再回头问设计者。

---

## 1. 意图与缘由

这是一个**学习项目**，不是生产系统。目的是用一套小而完整的代码，把 DDD 的战略设计与战术设计走一遍，让读者能指着代码说「这个概念在这里」。

判断成功的标准：

- 每个 DDD 概念都能在代码里找到唯一、清晰的落点
- 项目能一条命令跑起来，看到成功与补偿两条履约路径
- 测试本身能当教材：读测试就能理解聚合规则
- 文档能解释「为什么这样设计」，而不只是「这里有什么」

非目标：高并发、高可用、微服务、前端 UI、真实支付对接、权限体系。

反目标（即使技术上做得到，也视为失败）：

- 为了凑模式而造概念（例如为了演示工厂而写一个只有一行 `new` 的工厂）
- 引入与教学目标无关的框架或中间件
- 代码为了「看起来企业级」而变厚

---

## 2. 约束

| 项 | 决定 |
|---|---|
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 4.1.3 |
| 构建 | Maven Wrapper（Maven 3.10.2），单 Maven 模块 |
| 持久化 | H2 文件库 + Spring Data JDBC（不使用 JPA） |
| API 文档 | springdoc-openapi 3.2.1 |
| 代码风格 | 不用 Lombok；标识符英文，注释与文档中文 |
| 事件 | 进程内事件 + 事务性 Outbox；不引入消息中间件 |
| 测试 | 领域层零 mock；应用层尽量零 mock；不引入 Mockito 依赖 |
| 代码归属 | 所有代码（含测试）由实现者编写，使用者只读和运行 |

Spring Boot 4 的构建细节（与 3.x 不同，实现时注意）：

- Web starter 叫 `spring-boot-starter-webmvc`，H2 控制台是独立的 `spring-boot-h2console`
- 测试 starter 按模块拆分：`spring-boot-starter-webmvc-test`、`spring-boot-starter-data-jdbc-test`
- 两者都传递引入 `spring-boot-starter-test`，而后者含 `mockito-core` 与 `mockito-junit-jupiter`，需要在两处都排除
- 项目骨架由 start.spring.io 生成（bootVersion=4.1.3, javaVersion=21, dependencies=web,data-jdbc,h2）

---

## 3. 子域与限界上下文

| 上下文 | 子域类型 | 关键聚合 | 教学重点 |
|---|---|---|---|
| Sales 销售 | **核心域** | `Order` | 聚合不变量、状态机、领域事件、领域服务 |
| Inventory 库存 | 支撑域 | `StockItem` | 不同的统一语言、防超卖、并发控制 |
| Billing 结算 | 支撑域 | `Invoice`、`Payment` | 防腐层、外部系统模型翻译 |
| Fulfillment 履约 | 支撑域 | `FulfillmentProcess` | 流程管理器 / Saga、补偿 |
| 外部支付网关 | 外部系统 | — | 被防腐层包住，模型不渗入 |
| sharedkernel | — | `Money`、`Currency`、`Sku` | 共享内核及其代价 |

核心域选 Sales 的理由：订单的状态流转和金额规则是整个业务里最有判断价值的部分，其余上下文都是围绕它提供支撑。

---

## 4. 上下文映射

| 关系 | 参与方 | 落地方式 |
|---|---|---|
| 客户-供应商 | Sales（下游）→ Inventory（上游） | Sales 提出「要预占」，Inventory 决定契约与失败语义 |
| 防腐层 ACL | Billing → 外部支付网关 | 网关的字符串状态码、以「分」为单位的金额，在适配器里翻译 |
| 共享内核 | Sales ↔ Billing 共用 `Money`、`Currency` | 只放真正稳定的值类型；代价写进 ADR |
| 发布语言 | Inventory 发布 `StockReserved` 等集成事件 | 事件 schema 即跨上下文契约 |
| 分离方式 | 不强行建立关系的部分 | 文档说明「不是所有上下文都要有关系」 |

**领域事件与集成事件严格区分**：

- 领域事件：聚合内部产生，命名用过去式，只在本上下文内消费（`OrderPlaced`、`StockReserved`）
- 集成事件：跨上下文契约，有稳定的 payload schema 和版本意识，经 Outbox 发布
- 两者不共用同一个类，避免「改内部事件顺手破坏外部契约」

---

## 5. 统一语言

| 术语 | 上下文 | 含义 | 容易混淆的说法 |
|---|---|---|---|
| Order | Sales | 客户的一次购买意向及其生命周期 | 不要叫「单据」「记录」 |
| Place | Sales | 提交订单，进入不可编辑状态 | 不是「支付」，不是「确认」 |
| Confirm | Sales | 支付成功后系统确认订单 | 不是客户动作 |
| StockItem | Inventory | 某个 SKU 的库存账本 | 不是「商品」 |
| On hand | Inventory | 实际在库数量 | — |
| Reserved | Inventory | 已被订单占用但未出库的数量 | 不是「已售」 |
| Available | Inventory | `onHand - reserved` | — |
| Invoice | Billing | 应收凭据 | 不是「订单」 |
| Capture | Billing | 向支付网关实际扣款 | 不是「下单」 |
| FulfillmentProcess | Fulfillment | 一次订单履约的流程实例 | 不是「订单」 |

文档 `docs/01-ubiquitous-language.md` 会展开这张表，并给出反例。

---

## 6. 领域模型

### 6.1 sharedkernel

- `Money`：`BigDecimal amount`（scale 2，HALF_UP）+ `Currency currency`；不可变；跨币种相加抛异常
- `Currency`：ISO 4217 代码的封装，演示只启用 `CNY`，但类型本身不写死
- `Sku`：库存单位标识
- `AggregateRoot`、`DomainEvent`、`DomainException`：极薄的基类/接口，不含框架依赖

`Money` 放共享内核是**有意为之的取舍**：它足够稳定，且 Sales 与 Billing 必须对「金额」有一致理解。代价（耦合、升级同步）写进 ADR-0003。

### 6.2 Sales（核心域）

**聚合根 `Order`**

| 字段 | 类型 |
|---|---|
| `orderId` | `OrderId` |
| `customerId` | `CustomerId` |
| `lines` | `List<OrderLine>` |
| `status` | `OrderStatus` |
| `discount` | `Money` |
| `placedAt` | `Instant` |
| `cancelReason` | `String`（可空） |

**实体 `OrderLine`**：`lineId`、`sku`、`productName`、`unitPrice`、`quantity`

**值对象**：`OrderId`、`CustomerId`、`Quantity`（1..999）

**状态机**

```
DRAFT --place()--> PLACED --confirm()--> CONFIRMED
  |                   |
  +---cancel()--------+---cancel()--> CANCELLED
```

- 只有 `DRAFT` 允许增删行
- `place()` 要求至少一行，并调用定价策略计算折扣
- `CONFIRMED` 不可取消；`CANCELLED` 是终态

**不变量**

1. 行数量 `1..999`
2. 同一订单所有行币种一致
3. `total = Σ line.subtotal`
4. `payable = total - discount`，且 `discount >= 0`、`discount <= total`
5. `place()` 时至少一行
6. 非 `DRAFT` 状态拒绝增删行
7. 取消必须带非空原因

**领域服务 `PricingPolicy`**

- 接口：`Money discountFor(Order order)`
- 实现 `TieredPricingPolicy`：总件数 ≥ 10 打 95 折，≥ 20 打 9 折
- 纯函数，无依赖，便于单测

**领域事件**：`OrderLineAdded`、`OrderLineRemoved`、`OrderPlaced`、`OrderConfirmed`、`OrderCancelled`

**仓储**：`OrderRepository`（domain 接口），实现 `JdbcOrderRepository`

### 6.3 Inventory（支撑域）

**聚合根 `StockItem`**

| 字段 | 类型 |
|---|---|
| `sku` | `Sku` |
| `productName` | `String` |
| `onHand` | `int` |
| `reservations` | `List<Reservation>`（聚合内实体） |
| `version` | `long`（乐观锁） |

**实体 `Reservation`**：`orderId`、`quantity`

**行为**：`reserve(orderId, qty)`、`release(orderId)`、`restock(qty)`

**不变量**

1. `available = onHand - Σ reservations.quantity >= 0`
2. 同一 `orderId` 重复预占是幂等的（已存在则直接返回）
3. 释放不存在的预占是幂等的（无操作）
4. `restock` 数量必须 > 0

**集成事件**：`StockReserved`、`StockReservationFailed`、`StockReleased`（订单级，payload 含全部行）

### 6.4 Billing（支撑域）

**聚合根 `Invoice`**：`invoiceId`、`orderId`、`customerId`、`amount`、`status`（`ISSUED` / `PAID` / `VOIDED`）

**聚合根 `Payment`**：`paymentId`、`invoiceId`、`amount`、`status`（`PENDING` / `CAPTURED` / `FAILED`）、`gatewayRef`、`failureReason`

**端口 `PaymentGateway`**（domain 接口）：`CaptureResult capture(PaymentRequest request)`

**防腐层**：`ExternalPaymentGatewayAdapter` 实现 `PaymentGateway`，内部调用模拟的 `LegacyPaymentClient`：

- 外部模型用 `long amountInCents`、`String statusCode`（`"00"` 成功、`"51"` 余额不足、`"91"` 网关超时）
- 适配器负责单位换算与状态翻译，领域模型永远看不到字符串状态码

**集成事件**：`InvoiceIssued`、`PaymentCaptured`、`PaymentFailed`、`InvoiceVoided`

### 6.5 Fulfillment（支撑域）

**聚合根 `FulfillmentProcess`**

| 字段 | 类型 |
|---|---|
| `processId` | `ProcessId` |
| `orderId` | `OrderId` |
| `status` | `STARTED` / `STOCK_RESERVED` / `PAID` / `COMPLETED` / `COMPENSATING` / `CANCELLED` / `FAILED` |
| `failureReason` | `String`（可空） |
| `updatedAt` | `Instant` |

**行为**：`markStockReserved()`、`markPaid()`、`complete()`、`startCompensation(reason)`、`markCancelled()`

**应用层端口**（由基础设施实现，调用其他上下文的应用服务）：

- `InventoryPort.reserve(orderId, lines)` / `release(orderId)`
- `BillingPort.issueAndCapture(orderId, customerId, amount, currency)`
- `SalesPort.confirm(orderId)` / `cancel(orderId, reason)`

---

## 7. 关键流程

### 7.1 成功路径

```
POST /orders            -> Order(DRAFT)
POST /orders/{id}/place -> Order(PLACED) + OrderPlaced
  -> Outbox -> Fulfillment 创建 FulfillmentProcess(STARTED)
  -> Inventory.reserve  -> StockReserved      -> STOCK_RESERVED
  -> Billing.issueAndCapture -> PaymentCaptured -> PAID
  -> Sales.confirm      -> OrderConfirmed     -> COMPLETED
```

### 7.2 失败路径

```
库存不足:
  Inventory.reserve -> StockReservationFailed
  -> FulfillmentProcess(FAILED) -> Sales.cancel -> Order(CANCELLED)

支付失败:
  Billing.issueAndCapture -> PaymentFailed + Invoice(VOIDED)
  -> FulfillmentProcess(COMPENSATING)
  -> Inventory.release -> StockReleased
  -> Sales.cancel -> Order(CANCELLED) -> FulfillmentProcess(CANCELLED)
```

### 7.3 支付超时的语义

`FakePaymentGateway` 可以返回超时。**超时按失败处理**：`Payment(FAILED, reason=TIMEOUT)`，直接走补偿，不做查询重试。文档会说明真实系统需要「结果未知」状态与对账，本项目刻意不实现。

---

## 8. 关键设计决策

### 决策 1：Saga 用编排式，步骤同步调用

Fulfillment 由 `OrderPlaced` 集成事件触发，之后按步骤**同步调用** Inventory / Billing 的应用服务，每步一个事务、每步落库进度；失败则反向补偿。

理由：能完整讲清 Saga、补偿、流程状态机，又不引入等待态、超时、重试与最终一致的测试复杂度。全异步方案（命令事件 + 等待态 + 超时）写进 ADR 作为替代方案，不实现。

### 决策 2：Order 状态机

`DRAFT → PLACED → CONFIRMED`，旁路 `CANCELLED`。`/confirm` 改名 `/place`，避免与「支付后确认」撞名。支付后的确认由 Fulfillment 调用 Sales 应用服务完成，不对公网暴露。

### 决策 3：CQRS 与事件溯源的范围

- CQRS：新增 `order_summary_view` 投影表，由集成事件维护；`GET /orders?customerId=` 走读模型；启动时全量重建一次，不提供重建端点
- 事件溯源：`examples.eventsourcing` 独立包，内存事件存储 + `Account` 聚合 + 重放测试，不接 Spring、不接数据库、不进主流程

### 决策 4：多 SKU 预占

`OrderPlaced` 携带行项目；Inventory 的 `reserve(orderId, lines)` 在**一个事务内**循环多个 `StockItem`，全有或全无，发出一个订单级事件。`StockItem` 用乐观锁，冲突重试一次。文档说明真实系统会拆成按 SKU 的事件加补偿。

### 决策 5：「至少一行」不变量

创建时带行，DRAFT 期可增删，`place()` 时才校验至少一行。已 PLACED / CONFIRMED / CANCELLED 的订单拒绝改行。

### 决策 6：Billing 时序

`OrderPlaced` → 建 Invoice(ISSUED) → 发起 Payment → 成功则 CAPTURED + PAID；失败则 FAILED + VOIDED 并触发补偿；订单取消时发票作废。顺序是「预占库存 → 开票 → 支付」。

---

## 9. 架构

### 9.1 包结构

```
com.example.orders
├── sharedkernel
│   ├── Money, Currency, Sku, Quantity
│   ├── AggregateRoot, DomainEvent, DomainException
│   └── ...
├── sales
│   ├── domain        (Order, OrderLine, OrderStatus, PricingPolicy, OrderRepository, events)
│   ├── application   (OrderApplicationService, commands, queries)
│   ├── infrastructure(JdbcOrderRepository, outbox 写入)
│   └── api           (OrderController, DTO, 错误映射)
├── inventory
├── billing
├── fulfillment
├── projections       (order_summary_view 投影器与查询服务)
├── examples
│   └── eventsourcing (独立示例，不接 Spring)
└── bootstrap         (应用入口、配置、demo runner)
```

### 9.2 依赖规则（ArchUnit 守护）

1. `..domain..` 不得依赖 `org.springframework..`、`java.sql..`、`jakarta.persistence..`、`com.fasterxml..`
2. 上下文之间不得直接访问对方的 `domain` 包；跨上下文只能走 `application` 端口或集成事件
3. `..sharedkernel..` 不得依赖任何业务上下文
4. 领域测试包不得 import `org.mockito..`
5. `examples.eventsourcing` 不得依赖 Spring

### 9.3 分层职责

- **domain**：聚合、值对象、领域事件、领域服务、仓储接口、领域异常。纯 Java。
- **application**：用例编排、事务边界、DTO 转换、调用仓储与端口。不含业务规则。
- **infrastructure**：Spring Data JDBC 仓储实现、Outbox、外部网关适配器、定时任务。
- **api**：REST 控制器、请求/响应 DTO、错误映射。不含业务逻辑。

---

## 10. 持久化

| 表 | 说明 |
|---|---|
| `sales_order` | 订单主表，含 `version` |
| `sales_order_line` | 订单行，随聚合整体读写 |
| `inventory_stock_item` | 库存主表，含 `version` |
| `inventory_reservation` | 预占明细，`StockItem` 聚合的子表 |
| `billing_invoice` | 发票，含 `version` |
| `billing_payment` | 支付记录，含 `version` |
| `fulfillment_process` | 履约流程，含 `version` |
| `outbox_event` | 待发布集成事件 |
| `processed_event` | 消费幂等表 |
| `order_summary_view` | CQRS 读模型 |

约定：

- 一个聚合一次读写，不依赖懒加载
- 子表通过 `@MappedCollection` 映射
- 所有聚合带 `@Version` 乐观锁
- 不使用 JPA 的实体身份与脏检查

---

## 11. 事件与一致性

1. 应用服务在**同一事务**内保存聚合、把领域事件转成集成事件写入 `outbox_event`
2. `OutboxRelay` 每 200ms 取未发布事件，分发给订阅者，成功后标记 `published_at`
3. 投递语义为**至少一次**；每个消费者在自己的事务内写 `processed_event`，重复事件直接跳过
4. 消费失败不标记已发布，下轮重试
5. 投影器也走同一套机制，因此读模型是最终一致的

---

## 12. API

统一前缀 `/api`，错误响应使用 RFC 7807 `application/problem+json`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/orders` | 创建 DRAFT 订单（带行） |
| POST | `/api/orders/{id}/lines` | 追加行（仅 DRAFT） |
| DELETE | `/api/orders/{id}/lines/{lineId}` | 删除行（仅 DRAFT） |
| POST | `/api/orders/{id}/place` | 提交订单，触发履约 |
| POST | `/api/orders/{id}/cancel` | 取消，需 `reason` |
| GET | `/api/orders/{id}` | 订单详情（写模型） |
| GET | `/api/orders?customerId=` | 订单列表（读模型） |
| GET | `/api/fulfillment/{orderId}` | 履约进度 |
| GET | `/api/inventory/{sku}` | 库存快照 |
| POST | `/api/inventory/{sku}/stock` | 补货 |
| GET | `/api/invoices/{orderId}` | 发票与支付记录 |

---

## 13. 测试策略

| 层 | 测什么 | 依赖 |
|---|---|---|
| 聚合 / 领域服务 | 不变量、状态流转、事件 | 纯 JUnit 5 + AssertJ，零 Spring、零 mock |
| 应用服务 | 用例编排、事务、Outbox 写入 | `@DataJdbcTest` + 真实 Spring Data JDBC 仓储 + H2 内存库 |
| 防腐层 | 外部模型 → 领域模型翻译 | 手写 `FakePaymentGateway` / 固定报文 |
| 上下文集成 | 事件投递、幂等、投影 | `@SpringBootTest` + H2 |
| 架构 | 依赖规则 | ArchUnit |
| 端到端 | 成功路径、库存不足、支付失败 | 全真实，仅外部网关为 Fake |

无 mock 的具体做法：

- 聚合测试用真实对象，断言状态与 `pullDomainEvents()` 的结果，不用 `verify()`
- 应用层测试用真实仓储实现，不写 Fake 仓储
- 时间通过 `java.time.Clock` 注入，测试传 `Clock.fixed(...)`
- 全项目唯一手写测试替身：`FakePaymentGateway`（约 30 行，可编程返回成功 / 余额不足 / 超时）

---

## 14. 文档结构

| 文件 | 内容 |
|---|---|
| `README.md` | 项目总览、快速开始、学习路线 |
| `docs/01-ubiquitous-language.md` | 统一语言表与反例 |
| `docs/02-subdomains.md` | 子域划分与核心域论证 |
| `docs/03-context-map.md` | 上下文映射图（Mermaid）与每种关系的代码位置 |
| `docs/04-tactical-patterns.md` | DDD 概念 → 代码位置对照表 |
| `docs/05-architecture.md` | 分层、依赖规则、ArchUnit |
| `docs/06-events-and-consistency.md` | 领域事件 vs 集成事件、Outbox、幂等、投影 |
| `docs/07-testing-without-mocks.md` | 无 mock 测试的手法与理由 |
| `docs/08-scenario-walkthrough.md` | 两条履约路径的逐事件走读 |
| `docs/09-ddd-glossary.md` | DDD 术语速查 |
| `docs/adr/0001-modular-monolith.md` | 为什么模块化单体 |
| `docs/adr/0002-spring-data-jdbc.md` | 为什么不用 JPA |
| `docs/adr/0003-shared-kernel-money.md` | 为什么 Money 进共享内核，代价是什么 |
| `docs/adr/0004-outbox.md` | 为什么需要 Outbox |
| `docs/adr/0005-no-event-sourcing-in-main-flow.md` | 为什么事件溯源只做示例 |

---

## 15. 演示

`demo` profile 启动后自动跑两条路径，打印事件时间线：

1. 成功路径：下单 → 预占 → 支付 → 确认 → 完成
2. 失败路径：下单 → 预占 → 支付失败 → 释放库存 → 取消

同时提供 `OrderLifecycleScenarioTest`，用测试固化同样的时间线。

---

## 16. 里程碑

| 里程碑 | 内容 |
|---|---|
| M1 | Maven 骨架、共享内核、ArchUnit 基线 |
| M2 | Sales 战术设计（聚合、值对象、定价策略、仓储、API、单测） |
| M3 | Inventory（库存聚合、预占、并发、单测） |
| M4 | Billing + 防腐层（发票、支付、网关适配器、契约测试） |
| M5 | Fulfillment / Saga + Outbox + 幂等（成功与补偿路径） |
| M6 | CQRS 读模型与投影 |
| M7 | 事件溯源独立示例 |
| M8 | 文档、demo、端到端测试收尾 |

---

## 17. 留给实现者的决定

以下不改变设计意图，由实现者自行决定：

- 具体类名、方法名、Javadoc 写法
- 日志格式与 demo 输出排版
- DTO 与领域对象的转换方式（手写 mapper）
- 测试方法命名风格（建议 `should_xxx_when_yyy`）
- H2 连接串、文件路径、demo 数据的客户与 SKU 取值
- 异常层次的具体划分（在 `DomainException` 之下）
