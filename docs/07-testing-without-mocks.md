# 07 · 无 mock 测试

## 概念

Mock 框架（本项目排除的是 Mockito）擅长「隔离」：把被测对象的所有协作者替换成可编程的假对象，再用 `verify()` 断言调用发生。它在某些场景很有用，但在 DDD 教学项目里容易产生三个问题：

1. **测的是交互，不是结果**：`verify(repository).save(order)` 只能证明调用发生过，不能证明订单真的落库、事务真的回滚、outbox 真的写入。
2. **mock 会漂移**：接口新增方法、参数顺序变化，mock 测试可能仍然通过，真实实现却已不匹配。
3. **领域规则被稀释**：如果聚合依赖一堆 mock，测试就不再是「读起来像业务规则」的教材。

本项目的选择是：**优先用真实对象与真实数据库**；只在外部系统边界使用手写测试替身。项目在 `pom.xml` 中从两个测试 starter 排除了 `mockito-core` 与 `mockito-junit-jupiter`，并且有一个测试专门证明 Mockito 不在 classpath 上。

## 本项目在哪

### 测试分层

| 层 | 测什么 | 技术手段 | 代表测试 |
|---|---|---|---|
| 值对象 / 聚合 / 领域服务 | 不变量、状态迁移、事件、金额精度 | 纯 JUnit 5 + AssertJ，无 Spring、无数据库 | `src/test/java/com/example/orders/sales/domain/OrderTest.java`、`OrderStateTest.java`、`src/test/java/com/example/orders/inventory/domain/StockItemTest.java` |
| 应用服务切片 | 用例编排、事务、outbox 写入 | `@DataJdbcTest` + 真实 Spring Data JDBC 仓储 + H2 | `src/test/java/com/example/orders/sales/application/OrderApplicationServiceTest.java` |
| 仓储 | 领域对象 ↔ 表记录映射、乐观锁 | `@DataJdbcTest` + H2 | `src/test/java/com/example/orders/sales/infrastructure/JdbcOrderRepositoryTest.java` |
| REST API | 路由、状态码、problem+json、校验 | `@SpringBootTest` + `@AutoConfigureMockMvc` | `src/test/java/com/example/orders/sales/api/OrderControllerTest.java` |
| 防腐层 | 外部模型 → 本地模型翻译 | 手写网关/固定报文 | `src/test/java/com/example/orders/billing/infrastructure/ExternalPaymentGatewayAdapterTest.java` |
| 上下文集成 / Saga | 事件投递、幂等、补偿、并发 | `@SpringBootTest` + H2 + 真实 relay | `FulfillmentHappyPathTest`、`FulfillmentCompensationTest`、`ConcurrentReservationTest` |
| 读模型 | 投影、重复事件、重建 | `@SpringBootTest` + 真实 H2 | `src/test/java/com/example/orders/projections/OrderSummaryProjectorTest.java` |
| 演示 | 两条路径的事件时间线 | `@SpringBootTest` + `DemoRunner.runScenarios()` | `src/test/java/com/example/orders/bootstrap/DemoRunnerTest.java` |
| 架构 | 依赖规则 | ArchUnit（Task 23 加入） | `ArchitectureTest`（计划中） |

当前共有 31 个测试类；`./mvnw -B test` 运行 152 个测试方法。

### 替代 mock 的具体手法

#### 1. 用真实聚合与真实状态断言

```java
Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
order.addLine(...);
order.place(new TieredPricingPolicy(), Instant.now());

assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
assertThat(order.pullDomainEvents()).hasSize(1);
```

不 `verify` 聚合调用了什么，而是直接检查状态与事件结果。

#### 2. 用真实仓储 + H2

应用服务测试直接使用 `JdbcOrderRepository`、`JdbcOutboxRepository` 等真实实现，不写 Fake 仓储。这样 SQL、映射、`@Version` 乐观锁、事务回滚都被真实覆盖。

`@DataJdbcTest` 默认会回滚测试事务；涉及 outbox relay 的测试需要真实提交，因此 `OutboxRelayTest` 使用 `@Transactional(propagation = Propagation.NOT_SUPPORTED)` 并手动清理表。

#### 3. 时间用 `Clock` 注入

生产代码从 `Clock` 取当前时间（`bootstrap/ClockConfig` 提供 `Clock.systemUTC()`）。测试用 `FixedClockConfig` 提供 `Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)`，因此时间相关断言是确定的，不需要 mock 静态时间。

#### 4. 外部系统才用测试替身

全项目唯一的手写测试替身是 `src/test/java/com/example/orders/billing/support/FakePaymentGateway.java`，它实现 `PaymentGateway`：

- 构造时可传入一串 `CaptureResult`，按调用顺序返回；
- `alwaysReturning(result)` 每次返回同一结果；
- `enqueue(result)` 追加结果；
- `capturedRequests()` / `lastRequest()` 记录收到的请求，供断言幂等与参数；
- 队列耗尽时抛异常，避免测试意外复用结果。

它替代的是**真实外部支付网关**，而不是项目内部的协作者。在 `@SpringBootTest` 中通过 `@TestConfiguration` + `@Primary` 注入，不使用 `@MockBean`。

此外还有几个**小 stub**，它们不是通用 mock 框架：

| 替身 | 位置 | 用途 |
|---|---|---|
| `FakePaymentGateway` | `src/test/java/com/example/orders/billing/support/FakePaymentGateway.java` | 唯一完整的外部系统 Fake，可编程成功/拒绝/超时 |
| `OutboxRelayTest.TestDispatcher` | `src/test/java/com/example/orders/sharedkernel/outbox/OutboxRelayTest.java` | 记录收到的事件、可编程抛异常，验证 relay 的失败回滚 |
| `InventoryReservationFacadeTest.StubAttempt` | `src/test/java/com/example/orders/inventory/application/InventoryReservationFacadeTest.java` | 第一次抛乐观锁冲突、第二次成功，验证重试一次 |
| `FailingBillingConfig` 的匿名 `BillingPort` | `src/test/java/com/example/orders/fulfillment/FulfillmentFailureTest.java` | 模拟计费服务不可用，验证异常路由到补偿 |

这些替身都很小、手写、只实现一个接口；没有 `when(...).thenReturn(...)` 的 DSL，也没有对内部协作者做交互验证。

#### 5. 用 MockMvc 测协议，不测实现

`OrderControllerTest` 通过 HTTP 语义断言：

- 创建订单返回 `201` 与 `status=DRAFT`；
- 空取消原因返回 `400` 且 `Content-Type` 为 `application/problem+json`；
- 不存在的订单返回 `404`；
- 已确认订单取消返回 `409`。

它不会去 mock 应用服务；真实控制器、真实应用服务、真实 H2 一起工作。

### 为什么这样可以当教材

读 `OrderStateTest` 能直接看到订单允许哪些迁移；读 `FulfillmentCompensationTest` 能看到支付失败后库存 `available` 回到原值、订单 `CANCELLED`、流程 `CANCELLED`、只写一条 `fulfillment.failed.v1`。这些断言描述的是**业务结果**，不是「某方法被调了几次」。

## 反例与常见误解

### 反例 1：用 mock 仓储测应用服务

```java
when(orderRepository.findById(id)).thenReturn(Optional.of(order));
orderService.place(id);
verify(orderRepository).save(order);
```

这段测试无法发现：SQL 映射错误、乐观锁版本没有回写、outbox 没有和订单同事务提交、事务回滚后订单仍然被改。本项目用真实 `JdbcOrderRepository` + H2，正是为了覆盖这些。

### 反例 2：`@MockBean` 替换内部服务

`@MockBean` 会让 Spring 上下文里出现一个假 bean，测试通过但集成路径没被验证。本项目用 `@TestConfiguration` + `@Primary` 只在**外部网关**或**故意失败的端口**上替换，且断言仍然落在真实数据库状态上。

### 反例 3：为了「零 mock」而拒绝所有测试替身

零 mock 不等于零替身。外部支付网关如果真实调用会产生副作用、不可重复、需要网络；用 `FakePaymentGateway` 是合理且必要的。关键区别是：**替身只放在系统边界，内部协作用真实实现。**

### 误解 4：没有 Mockito 就不能测异常路径

异常路径通过真实对象触发：库存不足用真实 `StockItem` 的库存数；支付失败用 `FakePaymentGateway` 返回 `Declined`；计费服务不可用用一个小 `BillingPort` 抛异常。都不需要 Mockito。

### 误解 5：测试数量等于覆盖质量

本项目有 152 个测试方法，但重点不是数量，而是每个测试是否描述了一个业务事实。`OrderPlacedIntegrationEventContractTest` 只有 1 个测试，却专门钉住了「跨上下文能读取公开的行载荷」这一契约，价值很高。
