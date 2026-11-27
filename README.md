# 订单管理系统 · DDD 教学项目

一个**可运行、可阅读**的领域驱动设计（DDD）教学代码库。它用 Java 21 + Spring Boot 4.1.3 + Spring Data JDBC + H2 实现了一个简化的订单系统，覆盖战略设计（子域、限界上下文、上下文映射、统一语言）与战术设计（聚合、实体、值对象、领域事件、领域服务、仓储、应用服务、防腐层、Saga、CQRS 读模型）。

这不是生产系统。它刻意省略了高并发、高可用、微服务治理、真实支付对接、权限与前端 UI，把注意力放在「每个 DDD 概念在代码里的落点」以及「为什么这样取舍」。

> 设计意图见 [设计文档](docs/superpowers/specs/2026-11-27-order-management-ddd-design.md)，实施步骤见 [实施计划](docs/superpowers/plans/2026-11-27-order-management-ddd.md)。

## 你会看到什么

- 四个限界上下文：Sales（核心域）、Inventory、Billing、Fulfillment（支撑域）
- 一个共享内核：`Money`、`Currency`、`Sku`、`Quantity`、`OrderId`、`CustomerId`
- 领域事件与集成事件严格分离，集成事件经**事务性 Outbox** 发布
- 编排式 Saga：成功路径、库存不足、支付失败补偿
- CQRS 读模型：`order_summary_view` 由集成事件投影
- 一个**独立于主流程**的事件溯源示例
- 全项目不使用 Mockito，测试本身可以当教材

## 快速开始

前置条件：JDK 21，项目自带 Maven Wrapper，无需安装 Maven。

### 1. 运行 Demo（推荐第一次这样启动）

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

`demo` profile 会在启动后自动执行两条路径并打印事件时间线：

1. 成功路径：下单 → 预占库存 → 支付 → 确认订单 → 履约完成
2. 支付失败路径：下单 → 预占库存 → 支付失败 → 释放库存 → 取消订单

输出类似（时间戳与订单 ID 每次不同）：

```text
event timeline:
[...] sales.order.placed.v1
[...] inventory.stock.reserved.v1
[...] billing.payment.captured.v1
[...] sales.order.confirmed.v1
[...] fulfillment.completed.v1
[...] sales.order.placed.v1
[...] inventory.stock.reserved.v1
[...] billing.payment.failed.v1
[...] inventory.stock.released.v1
[...] sales.order.cancelled.v1
[...] fulfillment.failed.v1
```

Demo 启动的是普通 Web 应用，打印完时间线后进程会继续运行，按 `Ctrl+C` 退出。

### 2. 运行应用（手动调 API）

```bash
./mvnw spring-boot:run
```

默认端口 `8080`，数据写入当前目录下的 H2 文件库 `./data/orders`。可用入口：

- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- H2 Console：<http://localhost:8080/h2-console>（JDBC URL 与 `application.yml` 一致）

注意：**当前实现没有给 `OutboxRelay` 配置定时调度器**。通过 REST 下单后，`OrderPlaced` 会先落在 `outbox_event`；只有 Demo 或测试显式调用 `OutboxRelay.relayOnce()` 时，履约与读模型才会被驱动。这是教学项目刻意保留的确定性设计，详见 [事件与一致性](docs/06-events-and-consistency.md)。

### 3. 运行测试

```bash
./mvnw -B test
```

单独运行某个测试类：

```bash
./mvnw -B test -Dtest=OrderTest
./mvnw -B test -Dtest='OrderTest,StockItemTest,InvoiceTest'
```

完整校验（与实施计划的完成标准一致）：

```bash
./mvnw -B verify
```

## 学习路线

建议按「先战略、再战术、最后看协作」的顺序阅读。

| 顺序 | 读什么 | 重点 |
|---|---|---|
| 1 | [统一语言](docs/01-ubiquitous-language.md) | 同一个词在不同上下文中的含义 |
| 2 | [子域划分](docs/02-subdomains.md) | 为什么 Sales 是核心域 |
| 3 | [上下文映射](docs/03-context-map.md) | 四个上下文如何协作，谁依赖谁 |
| 4 | `sharedkernel`：`Money`、`Currency`、`Sku`、`Quantity`、`OrderId`、`CustomerId` | 值对象、共享内核的代价 |
| 5 | `sales/domain`：`Order`、`OrderLine`、`PricingPolicy`、`OrderPlaced` 等 | 聚合、不变量、状态机、领域事件、领域服务 |
| 6 | `sales/application` + `sales/infrastructure` | 用例编排、事务边界、领域事件 → 集成事件 |
| 7 | `inventory/domain` + `inventory/application` | 另一个统一语言、防超卖、乐观锁与重试 |
| 8 | `billing/domain` + `billing/infrastructure/ExternalPaymentGatewayAdapter` | 防腐层如何翻译外部模型 |
| 9 | `sharedkernel/outbox` | Outbox、relay、消费幂等 |
| 10 | `fulfillment/application`：`FulfillmentApplicationService`、`FulfillmentSteps` | 编排式 Saga、步骤事务、补偿 |
| 11 | `projections` | CQRS 读模型为何最终一致 |
| 12 | `examples/eventsourcing` | 事件溯源与主流程的边界 |
| 13 | `bootstrap/DemoRunner` + `DemoRunnerTest` | 两条路径的完整时间线 |

对照表：[战术模式](docs/04-tactical-patterns.md)、[架构](docs/05-architecture.md)、[场景走读](docs/08-scenario-walkthrough.md)、[术语表](docs/09-ddd-glossary.md)。

## 目录结构

```text
src/main/java/com/example/orders
├── OrdersApplication.java          Spring Boot 入口
├── sharedkernel
│   ├── Money / Currency / Sku / Quantity / OrderId / CustomerId
│   ├── AggregateRoot / DomainEvent / DomainException / NotFoundException
│   └── outbox                   集成事件契约、Outbox、relay、消费幂等
├── sales                         Sales 限界上下文（核心域）
│   ├── domain                    Order、OrderLine、状态机、定价策略、领域事件、仓储接口
│   ├── application               用例编排、命令、视图、领域事件 → 集成事件
│   ├── infrastructure            Spring Data JDBC 记录与手写映射
│   └── api                       REST 控制器、DTO、problem+json 错误映射
├── inventory                     Inventory 限界上下文
├── billing                       Billing 限界上下文 + 支付网关防腐层
├── fulfillment                   Fulfillment 限界上下文 + saga 编排与端口适配器
├── projections                   order_summary_view 投影器、查询、重建
├── examples/eventsourcing        独立事件溯源示例（不接 Spring）
├── config                        JdbcMappingConfiguration（标识符引号策略）
└── bootstrap                     时钟、定价策略、Outbox 组合根与 demo runner

src/main/resources
├── application.yml               默认 H2 文件库配置
├── application-demo.yml          demo profile：启动即运行演示
└── schema.sql                    全部表结构

src/test/java/com/example/orders
├── ...                           34 个测试类；无 Mockito
└── billing/support/FakePaymentGateway.java
```

## DDD 概念 → 代码速查

| DDD 概念 | 本项目落点 |
|---|---|
| 限界上下文 | `com.example.orders.sales` / `inventory` / `billing` / `fulfillment` |
| 核心域 | Sales（`sales/domain/Order`） |
| 支撑域 | Inventory、Billing、Fulfillment |
| 共享内核 | `sharedkernel/Money`、`Currency`、`Sku`、`Quantity`、`OrderId`、`CustomerId` |
| 聚合根 | `Order`、`StockItem`、`Invoice`、`Payment`、`FulfillmentProcess` |
| 实体 | `OrderLine`、`Reservation` |
| 值对象 | `Money`、`Currency`、`Sku`、`Quantity`、各 `*Id` |
| 领域事件 | `OrderPlaced`、`InvoiceIssued`、`PaymentCaptured`、`InvoiceVoided` 等 |
| 集成事件 | `sharedkernel/outbox/*IntegrationEvent`，类型字符串带 `.v1` |
| 领域服务 | `PricingPolicy` / `TieredPricingPolicy` |
| 仓储 | `OrderRepository` 等 domain 接口 + `Jdbc*Repository` 实现 |
| 应用服务 | `OrderApplicationService`、`InventoryApplicationService`、`BillingApplicationService` |
| 防腐层 | `billing/infrastructure/ExternalPaymentGatewayAdapter` |
| 流程管理器 / Saga | `FulfillmentApplicationService` + `FulfillmentSteps` |
| 事务性 Outbox | `sharedkernel/outbox/OutboxAppender`、`OutboxRelay` |
| 消费幂等 | `ProcessedEventStore` / `JdbcProcessedEventStore` |
| CQRS 读模型 | `projections/OrderSummaryProjector`、`order_summary_view` |
| 事件溯源 | `examples/eventsourcing`（独立示例） |

## 文档索引

- [01 统一语言](docs/01-ubiquitous-language.md)
- [02 子域](docs/02-subdomains.md)
- [03 上下文映射](docs/03-context-map.md)
- [04 战术模式](docs/04-tactical-patterns.md)
- [05 架构](docs/05-architecture.md)
- [06 事件与一致性](docs/06-events-and-consistency.md)
- [07 无 mock 测试](docs/07-testing-without-mocks.md)
- [08 场景走读](docs/08-scenario-walkthrough.md)
- [09 DDD 术语表](docs/09-ddd-glossary.md)
- ADR：[0001 模块化单体](docs/adr/0001-modular-monolith.md)、[0002 Spring Data JDBC](docs/adr/0002-spring-data-jdbc.md)、[0003 共享内核 Money](docs/adr/0003-shared-kernel-money.md)、[0004 Outbox](docs/adr/0004-outbox.md)、[0005 主流程不采用事件溯源](docs/adr/0005-no-event-sourcing-in-main-flow.md)

## 刻意的边界

- 支付网关是模拟的；**超时按失败处理**，不做查询重试与对账。
- 库存预占是「一个事务内多 SKU、全有或全无」；真实系统常拆成按 SKU 的事件并补偿。
- Outbox 是进程内发布，不接消息中间件；投递语义为至少一次。
- 生产默认每 200ms 调度 relay；测试与 Demo profile 关闭调度器并显式驱动 relay，保持确定性。
- 没有认证、授权、多租户、前端。
- 事件溯源只是独立示例，不参与主流程（见 [ADR-0005](docs/adr/0005-no-event-sourcing-in-main-flow.md)）。
