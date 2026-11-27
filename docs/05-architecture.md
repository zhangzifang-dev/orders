# 05 · 架构与依赖规则

## 概念

DDD 的分层不是「controller → service → dao」的技术分层，而是围绕领域模型的依赖方向：

```text
api / infrastructure  ──依赖──▶  application  ──依赖──▶  domain
                                      │                     ▲
                                      └──── 依赖共享内核 ────┘
```

关键规则：

1. **domain 不依赖框架**：领域层是纯 Java，不 import Spring、JDBC、Jackson、JPA。
2. **application 编排用例**：负责事务边界、加载/保存聚合、DTO 转换、写 outbox；不承载业务规则。
3. **infrastructure 实现端口**：Spring Data JDBC 仓储、外部网关适配器、跨上下文适配器。
4. **api 只做协议转换**：REST 控制器、请求/响应 DTO、错误映射，不含业务逻辑。
5. **跨上下文只能走应用端口或集成事件**：不能直接访问对方的 `domain`。

本项目是**模块化单体**：一个 Maven 模块、一个 Spring Boot 进程，但按限界上下文分包。为什么这样取舍见 [ADR-0001](adr/0001-modular-monolith.md)。

## 本项目在哪

### 顶层包结构

```text
com.example.orders
├── sharedkernel          跨上下文共享值对象、基类、Outbox（发布语言模块）
├── sales                 Sales 限界上下文（核心域）
├── inventory             Inventory 限界上下文
├── billing               Billing 限界上下文
├── fulfillment           Fulfillment 限界上下文
├── projections           查询侧读模型
├── examples.eventsourcing 独立事件溯源示例
├── config                持久化映射配置
└── bootstrap             应用入口、组合根、demo
```

每个业务上下文内部统一按四层组织：

```text
sales/
├── domain/          Order、OrderLine、OrderStatus、PricingPolicy、领域事件、OrderRepository、领域异常
├── application/     OrderApplicationService、命令、OrderView
├── infrastructure/  OrderRecord、OrderLineRecord、OrderJdbcDao、JdbcOrderRepository
└── api/             OrderController、请求/响应 DTO、OrderRequestMapper、ApiExceptionHandler
```

### 依赖规则的代码证据

| 规则 | 正例 | 反例（本项目刻意避免） |
|---|---|---|
| domain 不依赖 Spring | `sales/domain/Order.java` 只 import `java.*` 和 `sharedkernel` | 不在聚合上写 `@Entity`、`@Service` |
| domain 不依赖 JDBC | `OrderRepository` 是接口，方法参数/返回值是领域类型 | 不让 `Order` 持有 `JdbcTemplate` |
| 应用层不写业务规则 | `OrderApplicationService.place` 只调用 `order.place(...)`、保存、写 outbox | 不在应用服务里判断「至少一行」 |
| 基础设施实现领域端口 | `JdbcOrderRepository implements OrderRepository` | 不让领域层 import `OrderRecord` |
| 跨上下文走端口 | `FulfillmentSteps` 依赖 `InventoryPort` 等接口 | 不 import `inventory.domain.*` |
| 跨上下文走集成事件 | `OrderSummaryProjector` 监听 `OrderPlacedIntegrationEvent` | 不直接查询 `sales_order` 表维护投影 |

### ArchUnit：计划中的守护者

Task 23 会在 `src/test/java/com/example/orders/ArchitectureTest.java` 加入 ArchUnit 测试，用来把上述规则变成可执行约束。**在 Task 23 完成前，这个文件尚不存在**；本节描述的是计划中的规则，Task 23 后会由 `ArchitectureTest` 强制执行。

计划规则（与实施计划一致）：

1. `..domain..` 不得依赖 `org.springframework..`、`java.sql..`、`jakarta.persistence..`、`com.fasterxml..`；
2. `sales.domain` 不得依赖 `inventory`、`billing`、`fulfillment`、`projections`；
3. `inventory.domain`、`billing.domain`、`fulfillment.domain` 同样不得依赖其他业务上下文；
4. `..domain..` 不得依赖 `..application..`、`..infrastructure..`、`..api..`；
5. `examples.eventsourcing` 不得依赖 Spring。

注意：真实的 `ArchitectureTest` 会用 `@AnalyzeClasses(packages = "com.example.orders")` 扫描全部类；规则以 Task 23 的代码为准。如果规则失败，应当修依赖方向，而不是放宽规则。

### 持久化架构

本项目**不使用 JPA**，而是 Spring Data JDBC + 手写映射：

- 每个表对应一个 `*Record`（`OrderRecord`、`StockItemRecord`、`InvoiceRecord`、`PaymentRecord`、`FulfillmentProcessRecord` 等），只用于持久化；
- `Jdbc*Repository` 在领域对象与记录之间转换，聚合整体读写；
- 聚合带 `@Version` 乐观锁；`Jdbc*Repository.save` 根据是否存在决定传入 `null`（新增）或当前版本（更新），保存后把新版本写回聚合；
- `JdbcMappingConfiguration` 在 `com.example.orders.config` 中**覆盖整个映射上下文的标识符引号策略**：Spring Data JDBC 4 默认强制引用标识符，而 `schema.sql` 的 DDL 未加引号、H2 会折叠为大写，两者混用会找不到表/列。该类使用 `JdbcMappingContext.forPlainIdentifiers(...)` 统一为不加引号。

为什么不用 JPA，见 [ADR-0002](adr/0002-spring-data-jdbc.md)。

### 事务边界

| 场景 | 事务边界 | 代码 |
|---|---|---|
| Sales 下单/取消/确认 | 应用服务方法 `@Transactional`；聚合保存与 outbox 追加同一事务 | `OrderApplicationService` |
| Inventory 多 SKU 预占 | `@Transactional`，全有或全无 | `InventoryApplicationService.reserve` |
| Inventory 并发重试 | 非事务门面 `InventoryReservationFacade` + 每次尝试 `REQUIRES_NEW` | `TransactionalInventoryReservationAttempt` |
| Billing 开票+扣款 | `@Transactional`；发票、支付、outbox 同一事务 | `BillingApplicationService.issueAndCapture` |
| Outbox 单条发布 | `OutboxPublisher.publishOne @Transactional` | 分发与 `published_at` 同一事务 |
| Fulfillment Saga | **编排者无事务**；每个步骤 `REQUIRES_NEW` | `FulfillmentApplicationService` + `FulfillmentSteps` |

Fulfillment 的这种设计是刻意的：Saga 跨多个上下文，不能用一个数据库事务包住所有步骤；每步独立提交，失败后按已提交的进度补偿。补偿是**尽力而为**的：单个补偿动作失败会被记录并继续执行其他动作，最后 `finalizeCancelled` 会重新读取最新状态并自愈到 `CANCELLED`。

### 组合根

- `bootstrap/ClockConfig`：提供 `Clock.systemUTC()`；
- `bootstrap/SalesConfig`：把纯领域的 `TieredPricingPolicy` 暴露为 `PricingPolicy` bean；
- `bootstrap/OutboxConfig`：装配 `OutboxAppender`、`OutboxPublisher`、`OutboxRelay`、`IntegrationEventRegistry`、`SpringEventDispatcher`；
- `config/JdbcMappingConfiguration`：持久化映射策略。

`OutboxAppender` 刻意不加 `@Component`：它必须与调用方事务共用边界，由组合根显式装配更容易看清这一点。

## 反例与常见误解

### 反例 1：domain 里出现 `@Entity` / `@Table`

那会把领域模型绑死在持久化框架上，领域测试必须启动 Spring，聚合也无法保持纯 Java。本项目的 `@Table` 只出现在 `infrastructure/*Record.java`。

### 反例 2：应用服务返回聚合

`OrderApplicationService.get(...)` 返回 `OrderView`，而不是 `Order`。如果返回聚合，REST 层可能直接调用领域方法，绕过事务与 outbox；同时会把领域对象暴露成 API 契约。本项目在应用层完成视图转换。

### 反例 3：跨上下文直接查表

例如 Fulfillment 直接 `SELECT * FROM sales_order` 来确认订单状态。这会让 Sales 的表结构成为隐式契约。本项目通过 `SalesPortAdapter` 调用 `OrderApplicationService`，或通过集成事件通信。

### 误解 4：分层越多越好

本项目没有额外的通用 `mapper`、`facade`、`assembler` 层。`OrderRequestMapper` 只做 REST DTO ↔ 应用层命令/视图的翻译；`InventoryReservationFacade` 是库存并发重试的刻意门面（把重试放在事务代理之外），不是通用层；仓储内部手写映射。分层是为了隔离变化，不是为了堆类。

### 误解 5：ArchUnit 规则已经生效

当前 `src/test/java/com/example/orders/ArchitectureTest.java` **尚未创建**，要等 Task 23。现在依赖规则靠代码评审与包结构约束；Task 23 后才由测试自动守护。
