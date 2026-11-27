# 订单管理系统（DDD 教学项目）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Java 21 + Spring Boot 4.1.3 建一个可运行、可阅读的 DDD 教学项目，覆盖战略设计与战术设计的主要知识点。

**Architecture:** 模块化单体，按限界上下文分包；Sales 为核心域，Inventory / Billing / Fulfillment 为支撑域；跨上下文通过应用端口同步调用，事件经事务性 Outbox 发布；CQRS 读模型由集成事件投影；事件溯源只做独立示例。

**Tech Stack:** Java 21、Spring Boot 4.1.3、Spring Data JDBC、H2、springdoc 3.2.1、JUnit 5、AssertJ、ArchUnit 1.5.1。不引入 Mockito。

**Spec:** `docs/superpowers/specs/2026-11-27-order-management-ddd-design.md`

## Global Constraints

- Java 21；Spring Boot 4.1.3；Maven Wrapper 3.10.2；单 Maven 模块
- Web starter 是 `spring-boot-starter-webmvc`；测试 starter 是 `spring-boot-starter-webmvc-test` 与 `spring-boot-starter-data-jdbc-test`
- 两个测试 starter 都要排除 `org.mockito:mockito-core` 和 `org.mockito:mockito-junit-jupiter`
- 不用 Lombok；标识符英文，注释与文档中文
- 领域层不依赖 Spring / JDBC / Jackson；领域测试零 mock
- 金额一律 `BigDecimal`，scale 2，`RoundingMode.HALF_UP`
- 所有聚合带 `@Version` 乐观锁；一个聚合一次读写
- 集成事件类型字符串带版本，例如 `sales.order.placed.v1`
- 每个任务结束时 `./mvnw test` 必须全绿，并提交一次
- Boot 4 的测试注解改了包：`@DataJdbcTest` 在 `org.springframework.boot.data.jdbc.test.autoconfigure`，`@AutoConfigureMockMvc` 在 `org.springframework.boot.webmvc.test.autoconfigure`；`@SpringBootTest` / `@TestConfiguration` 仍在 `org.springframework.boot.test.context`

## Review Focus

以下五类输入/条件最容易被实现者忽略，却最可能让使用者踩坑。每条都已在对应任务的测试里钉住：

1. **金额精度与币种**：`0.005` 的舍入、跨币种相加、零金额——必须抛异常或按 HALF_UP 得到确定结果（Task 2）
2. **同一 SKU 并发预占**：两个订单同时抢最后一件，`available` 不得为负，必须有一方失败（Task 11）
3. **重复投递同一事件**：Outbox 重试导致同一事件被消费两次，订单/库存/发票状态不得重复变更（Task 16、17）
4. **非法输入**：负数量、空取消原因、不存在的订单、已确认订单再取消——返回确定的 4xx，不抛 500（Task 4、8）
5. **支付网关未知状态码**：外部返回未定义的 `statusCode` 时，按失败处理并记录原因，不静默当成功（Task 13）

---

### Task 1: 项目骨架与构建基线

**Files:**
- Create: `pom.xml`、`mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`、`.gitignore`、`src/main/java/com/example/orders/OrdersApplication.java`、`src/main/resources/application.yml`、`src/test/resources/application-test.yml`、`src/test/java/com/example/orders/OrdersApplicationTests.java`

**Interfaces:**
- Consumes: 无
- Produces: 可构建的 Spring Boot 应用；`@SpringBootTest` 可用；`./mvnw` 可用

- [ ] **Step 1: 用 start.spring.io 生成骨架**

```bash
cd /home/zzf/projects/orders
curl -sS -G https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=4.1.3 -d javaVersion=21 \
  -d groupId=com.example -d artifactId=orders -d name=orders \
  -d packageName=com.example.orders -d dependencies=web,data-jdbc,h2 \
  -o /tmp/orders-skeleton.zip
unzip -o /tmp/orders-skeleton.zip -d .
rm -f HELP.md
```

- [ ] **Step 2: 改 `pom.xml`**

在 `<properties>` 加：

```xml
<springdoc.version>3.2.1</springdoc.version>
<archunit.version>1.5.1</archunit.version>
```

加依赖：

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>${springdoc.version}</version>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>${archunit.version}</version>
  <scope>test</scope>
</dependency>
```

给 `spring-boot-starter-webmvc-test` 和 `spring-boot-starter-data-jdbc-test` 都加：

```xml
<exclusions>
  <exclusion><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId></exclusion>
  <exclusion><groupId>org.mockito</groupId><artifactId>mockito-junit-jupiter</artifactId></exclusion>
</exclusions>
```

- [ ] **Step 3: 配置数据源**

`src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: orders
  datasource:
    url: jdbc:h2:file:./data/orders;AUTO_SERVER=TRUE
    username: sa
    password: ""
  sql:
    init:
      mode: always
  h2:
    console:
      enabled: true
```

`src/test/resources/application-test.yml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orders-test;DB_CLOSE_DELAY=-1
```

- [ ] **Step 4: 写测试**

`src/test/java/com/example/orders/OrdersApplicationTests.java`：

```java
@SpringBootTest
@ActiveProfiles("test")
class OrdersApplicationTests {
    @Test void contextLoads() {}
    @Test void mockitoIsNotOnClasspath() {
        assertThatThrownBy(() -> Class.forName("org.mockito.Mockito"))
            .isInstanceOf(ClassNotFoundException.class);
    }
}
```

- [ ] **Step 5: 运行**

Run: `./mvnw -B test`
Expected: BUILD SUCCESS，两个测试通过

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "chore: bootstrap Spring Boot 4.1.3 project without Mockito"
```

---

### Task 2: sharedkernel 值对象与基类

**Files:**
- Create: `src/main/java/com/example/orders/sharedkernel/{Money,Currency,Sku,Quantity,OrderId,CustomerId,DomainEvent,AggregateRoot,DomainException,NotFoundException}.java`
- Test: `src/test/java/com/example/orders/sharedkernel/{MoneyTest,QuantityTest,SkuTest,AggregateRootTest}.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `Money.of(String, Currency)`、`Money.of(BigDecimal, Currency)`、`Money.zero(Currency)`
  - `Money.add/subtract/multiply(int)/isZero/isNegative/isGreaterThan(Money)`，`Comparable<Money>`
  - `Currency.CNY`、`Currency.of(String)`
  - `Quantity.of(int)`（1..999）、`Quantity.value()`
  - `Sku.of(String)`（去空格、转大写、非空）
  - `OrderId.of(UUID)`、`OrderId.newId()`、`OrderId.value()`
  - `CustomerId.of(UUID)`、`CustomerId.value()`
  - `DomainEvent`：`eventId()`、`occurredAt()`、`eventType()`、`aggregateId()`
  - `AggregateRoot.registerEvent(DomainEvent)`、`pullDomainEvents()`
  - `AggregateRoot.version()`、`AggregateRoot.assignVersion(long)`（承载乐观锁版本；这是有意的务实妥协，文档里要说明）

- [ ] **Step 1: 写失败测试**

`MoneyTest` 关键断言：

```java
@Test void roundsToTwoDecimalsHalfUp() {
    assertThat(Money.of("10.005", Currency.CNY).amount()).isEqualByComparingTo("10.01");
}
@Test void rejectsCurrencyMismatch() {
    assertThatThrownBy(() -> Money.of("1.00", Currency.CNY).add(Money.of("1.00", Currency.of("USD"))))
        .isInstanceOf(DomainException.class);
}
@Test void subtractBelowZeroIsAllowedButReported() {
    Money result = Money.of("1.00", Currency.CNY).subtract(Money.of("2.00", Currency.CNY));
    assertThat(result.isNegative()).isTrue();
}
```

`QuantityTest`：`Quantity.of(0)`、`Quantity.of(1000)` 抛 `DomainException`；`Quantity.of(1)`、`Quantity.of(999)` 通过。

`SkuTest`：`Sku.of(" abc-1 ")` 得到 `ABC-1`；`Sku.of("  ")` 抛异常。

`AggregateRootTest`：注册两个事件后 `pullDomainEvents()` 返回两个，再调用返回空列表。

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=MoneyTest`
Expected: 编译失败或测试失败（类不存在）

- [ ] **Step 3: 实现**

`Money` 为 `record`，构造器里 `setScale(2, HALF_UP)` 并校验非 null；`Currency` 为 `record`，校验 `[A-Z]{3}`；`OrderId` / `CustomerId` 为 `record(UUID value)`；`Quantity` 为 `record(int value)`；`Sku` 为 `record(String value)`。

`AggregateRoot` 用 `private final List<DomainEvent> domainEvents = new ArrayList<>()`，`pullDomainEvents()` 返回 `List.copyOf` 后 `clear()`。

- [ ] **Step 4: 运行通过**

Run: `./mvnw -B test -Dtest='MoneyTest,QuantityTest,SkuTest,AggregateRootTest'`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "feat(sharedkernel): add money, currency and identifier value objects"
```

---

### Task 3: Order 聚合——行与总额

**Files:**
- Create: `src/main/java/com/example/orders/sales/domain/{Order,OrderLine,LineId,OrderStatus,OrderLineAdded,OrderLineRemoved}.java`
- Test: `src/test/java/com/example/orders/sales/domain/OrderTest.java`

**Interfaces:**
- Consumes: Task 2 的 `Money`、`Currency`、`Sku`、`Quantity`、`OrderId`、`CustomerId`、`AggregateRoot`
- Produces:
  - `Order.draft(OrderId, CustomerId, Currency)`
  - `Order.addLine(LineId, Sku, String productName, Money unitPrice, Quantity quantity)`
  - `Order.removeLine(LineId)`
  - `Order.total()`、`Order.payable()`、`Order.lines()`、`Order.status()`
  - `OrderLine.subtotal()`、`OrderLine.lineId()`

- [ ] **Step 1: 写失败测试**

```java
@Test void totalIsSumOfLineSubtotals() {
    Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
    order.addLine(LineId.newId(), Sku.of("SKU-1"), "键盘", Money.of("199.00", Currency.CNY), Quantity.of(2));
    order.addLine(LineId.newId(), Sku.of("SKU-2"), "鼠标", Money.of("99.00", Currency.CNY), Quantity.of(1));
    assertThat(order.total()).isEqualTo(Money.of("497.00", Currency.CNY));
}
@Test void rejectsLineWithDifferentCurrency() {
    // unitPrice 用 USD，订单币种 CNY -> DomainException
}
@Test void removeLineUpdatesTotal() { /* 加两行删一行后 total 只剩另一行 */ }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=OrderTest`
Expected: FAIL（Order 不存在）

- [ ] **Step 3: 实现**

`Order` 持有 `Currency currency`；`addLine` 校验 `unitPrice.currency().equals(currency)`；`total()` 用 `Money.zero(currency)` 累加；`payable()` 暂等于 `total()`（折扣在 Task 4 加入）。`OrderLine` 为不可变类，`subtotal()` = `unitPrice.multiply(quantity.value())`。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=OrderTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(sales): add order aggregate with lines and totals"
```

---

### Task 4: Order 状态机与定价策略

**Files:**
- Create: `src/main/java/com/example/orders/sales/domain/{PricingPolicy,TieredPricingPolicy,OrderPlaced,OrderConfirmed,OrderCancelled,InvalidOrderStateException}.java`
- Modify: `src/main/java/com/example/orders/sales/domain/Order.java`
- Test: `src/test/java/com/example/orders/sales/domain/{OrderStateTest,TieredPricingPolicyTest}.java`

**Interfaces:**
- Consumes: Task 3 的 `Order`
- Produces:
  - `Order.place(PricingPolicy, Instant)`
  - `Order.confirm(Instant)`、`Order.cancel(String reason, Instant)`
  - `Order.discount()`、`Order.isDraft()`
  - `PricingPolicy.discountFor(Order)`、`TieredPricingPolicy` 无参构造

- [ ] **Step 1: 写失败测试**

```java
@Test void placeRequiresAtLeastOneLine() {
    Order order = Order.draft(OrderId.newId(), CustomerId.of(UUID.randomUUID()), Currency.CNY);
    assertThatThrownBy(() -> order.place(new TieredPricingPolicy(), Instant.now()))
        .isInstanceOf(DomainException.class);
}
@Test void placedOrderRejectsLineChanges() { /* place 后 addLine/removeLine 抛 InvalidOrderStateException */ }
@Test void confirmOnlyFromPlaced() { /* DRAFT.confirm -> 异常；PLACED.confirm -> CONFIRMED */ }
@Test void cancelRequiresNonBlankReason() { /* "  " -> 异常 */ }
@Test void confirmedOrderCannotBeCancelled() { /* -> 异常 */ }
@Test void tenItemsGetFivePercentOff() {
    // 10 件单价 100 -> total 1000.00，discount 50.00，payable 950.00
}
@Test void twentyItemsGetTenPercentOff() { /* 20 件 -> discount 200.00 */ }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest='OrderStateTest,TieredPricingPolicyTest'`
Expected: FAIL

- [ ] **Step 3: 实现**

`place` 顺序：校验 `DRAFT` 且至少一行 → `discount = policy.discountFor(this)` → `payable = total - discount` → `status = PLACED` → `registerEvent(new OrderPlaced(...))`。

`TieredPricingPolicy`：总件数 ≥ 20 取 `total.multiply(BigDecimal("0.10"))`，≥ 10 取 `0.05`，否则 `Money.zero`；结果 scale 2 HALF_UP。

`confirm` 只允许 `PLACED`；`cancel` 只允许 `DRAFT` / `PLACED`，原因 `isBlank()` 抛异常。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest='OrderStateTest,TieredPricingPolicyTest'`
Expected: PASS

```bash
git add -A && git commit -m "feat(sales): add order state machine and tiered pricing"
```

---

### Task 5: Outbox 存储与中继

**Files:**
- Create: `src/main/java/com/example/orders/sharedkernel/outbox/{IntegrationEvent,OutboxEvent,OutboxRepository,JdbcOutboxRepository,OutboxAppender,OutboxRelay,IntegrationEventRegistry,IntegrationEventDispatcher}.java`
- Create: `src/main/java/com/example/orders/sharedkernel/outbox/OrderPlacedIntegrationEvent.java`（`OrderLinePayload` 同文件）
- Create: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/sharedkernel/outbox/OutboxRelayTest.java`

**Interfaces:**
- Consumes: Task 2 的 `DomainEvent`、`OrderId`、`CustomerId`、`Money`、`Sku`
- Produces:
  - `IntegrationEvent`：`eventId()`、`eventType()`、`aggregateType()`、`aggregateId()`、`occurredAt()`
  - `OutboxAppender.append(IntegrationEvent)`
  - `OutboxRelay.relayOnce()`（返回本轮发布条数）
  - `IntegrationEventRegistry.register(String type, Class<? extends IntegrationEvent>)`
  - `IntegrationEventDispatcher.dispatch(IntegrationEvent)`

- [ ] **Step 1: 建表**

`src/main/resources/schema.sql`：

```sql
CREATE TABLE IF NOT EXISTS outbox_event (
  id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id VARCHAR(60) NOT NULL,
  payload CLOB NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  published_at TIMESTAMP WITH TIME ZONE
);
```

- [ ] **Step 2: 写失败测试**

```java
@DataJdbcTest
@Import({JdbcOutboxRepository.class, OutboxAppender.class, OutboxRelay.class,
         IntegrationEventRegistry.class, TestDispatcher.class})
class OutboxRelayTest {
    @Test void appendsAndPublishes() {
        appender.append(event("sales.order.placed.v1"));
        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(dispatcher.received).hasSize(1);
        assertThat(relay.relayOnce()).isZero();
    }
    @Test void failingDispatchLeavesEventUnpublished() { /* dispatcher 抛异常 -> published_at 仍为 null */ }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw -B test -Dtest=OutboxRelayTest`
Expected: FAIL

- [ ] **Step 4: 实现**

`OutboxAppender` 用 Jackson 把事件序列化成 JSON 写入 `outbox_event`，`event_id` 直接用 `IntegrationEvent.eventId()`。
`OutboxAppender` 构造注入 `ObjectMapper`；`@DataJdbcTest` 不会自动装配 Jackson，测试里用 `@TestConfiguration` 提供 `ObjectMapper` 并注册 `JavaTimeModule`。
`OutboxRelay.relayOnce()`：查 `published_at IS NULL` 的 50 条，逐条反序列化（按 `event_type` 查注册表）→ `dispatcher.dispatch` → `markPublished`。**不在 relay 上开事务**，每条交给 `OutboxPublisher.publishOne`（`@Transactional`）执行，失败则整条回滚并中断本轮。

- [ ] **Step 5: 运行通过并提交**

Run: `./mvnw -B test -Dtest=OutboxRelayTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(sharedkernel): add transactional outbox and relay"
```

---

### Task 6: Sales 持久化

**Files:**
- Create: `src/main/java/com/example/orders/sales/domain/OrderRepository.java`
- Create: `src/main/java/com/example/orders/sales/infrastructure/{OrderRecord,OrderLineRecord,OrderJdbcDao,JdbcOrderRepository}.java`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/sales/infrastructure/JdbcOrderRepositoryTest.java`

**Interfaces:**
- Consumes: Task 3/4 的 `Order`
- Produces: `OrderRepository.save(Order)`、`findById(OrderId)`、`findByCustomerId(CustomerId)`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE IF NOT EXISTS sales_order (
  id UUID PRIMARY KEY, customer_id UUID NOT NULL, status VARCHAR(20) NOT NULL,
  currency VARCHAR(3) NOT NULL, discount_amount DECIMAL(19,2) NOT NULL,
  placed_at TIMESTAMP WITH TIME ZONE, cancel_reason VARCHAR(500), version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS sales_order_line (
  id UUID PRIMARY KEY, order_id UUID NOT NULL, sku VARCHAR(60) NOT NULL,
  product_name VARCHAR(200) NOT NULL, unit_price_amount DECIMAL(19,2) NOT NULL,
  quantity INT NOT NULL
);
```

- [ ] **Step 2: 写失败测试**

```java
@DataJdbcTest
@Import(JdbcOrderRepository.class)
class JdbcOrderRepositoryTest {
    @Test void roundTripsOrderWithLines() { /* save 两行 -> findById -> total 一致 */ }
    @Test void optimisticLockRejectsStaleWrite() { /* 同一订单保存两次，第二次版本冲突 */ }
}
```

- [ ] **Step 3: 实现**

`OrderRecord` 用 `@Table("sales_order")`、`@Id UUID id`、`@Version Long version`、`@MappedCollection(idColumn = "order_id") Set<OrderLineRecord> lines`。`OrderLineRecord` 带 `@Id UUID id`。
`JdbcOrderRepository` 手写 domain ↔ record 映射，`save` 时 `dao.save(record)`。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=JdbcOrderRepositoryTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(sales): persist order aggregate with Spring Data JDBC"
```

---

### Task 7: Sales 应用服务

**Files:**
- Create: `src/main/java/com/example/orders/sales/application/{OrderApplicationService,CreateOrderCommand,CreateOrderLineCommand,AddOrderLineCommand}.java`
- Create: `src/main/java/com/example/orders/sales/application/OrderView.java`
- Create: `src/main/java/com/example/orders/bootstrap/ClockConfig.java`（提供 `Clock.systemUTC()` bean）
- Modify: `src/main/java/com/example/orders/sharedkernel/outbox/`（注册 `OrderPlacedIntegrationEvent`）
- Test: `src/test/java/com/example/orders/sales/application/OrderApplicationServiceTest.java`

**Interfaces:**
- Consumes: Task 5 的 `OutboxAppender`；Task 6 的 `OrderRepository`
- Produces:
  - `OrderApplicationService.create(CreateOrderCommand) -> OrderId`
  - `addLine(OrderId, AddOrderLineCommand)`、`removeLine(OrderId, LineId)`
  - `place(OrderId)`、`cancel(OrderId, String reason)`
  - `confirmOrder(OrderId)`（供 Fulfillment 调用，写 `OrderConfirmedIntegrationEvent`）
  - `get(OrderId) -> OrderView`、`listAll() -> List<OrderView>`（供读模型重建）

- [ ] **Step 1: 写失败测试**

```java
@DataJdbcTest
@Import({OrderApplicationService.class, JdbcOrderRepository.class, JdbcOutboxRepository.class,
         OutboxAppender.class, TieredPricingPolicy.class, FixedClockConfig.class})
class OrderApplicationServiceTest {
    @Test void placeWritesOrderAndOutboxInOneTransaction() {
        OrderId id = service.create(cmdWithOneLine());
        service.place(id);
        assertThat(orderRepository.findById(id).orElseThrow().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(outboxRepository.countUnpublished()).isEqualTo(1);
    }
    @Test void placeWithoutLinesThrowsAndWritesNothing() { /* 断言订单仍 DRAFT、outbox 为 0 */ }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=OrderApplicationServiceTest`
Expected: FAIL

- [ ] **Step 3: 实现**

测试里的 `FixedClockConfig` 是一个 `@TestConfiguration`，提供 `Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)`。

`place` 标注 `@Transactional`：加载 → `order.place(pricingPolicy, clock.instant())` → `repository.save` → 把 `pullDomainEvents()` 中的 `OrderPlaced` 映射成 `OrderPlacedIntegrationEvent` 并 `outboxAppender.append`。
`cancel` 同理写 `OrderCancelledIntegrationEvent`；`confirm` 由 Fulfillment 通过 `confirmOrder(OrderId)` 调用并写 `OrderConfirmedIntegrationEvent`。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=OrderApplicationServiceTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(sales): add order application service with outbox writes"
```

---

### Task 8: Sales REST API

**Files:**
- Create: `src/main/java/com/example/orders/sales/api/{OrderController,OrderRequestMapper,ApiExceptionHandler}.java`
- Create: `src/main/java/com/example/orders/sales/api/dto/{CreateOrderRequest,CreateOrderLineRequest,AddLineRequest,OrderResponse,OrderLineResponse}.java`
- Test: `src/test/java/com/example/orders/sales/api/OrderControllerTest.java`

**Interfaces:**
- Consumes: Task 7 的 `OrderApplicationService`
- Produces: `/api/orders` 下 7 个端点（见设计文档 §12）

- [ ] **Step 1: 写失败测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {
    @Test void createsDraftOrder() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content("""
            {"customerId":"%s","currency":"CNY","lines":[
              {"sku":"SKU-1","productName":"键盘","unitPrice":"199.00","quantity":2}]}
            """.formatted(customerId)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status").value("DRAFT"));
    }
    @Test void cancelWithoutReasonReturns400() { /* reason 为 "  " -> 400 problem+json */ }
    @Test void unknownOrderReturns404() { /* -> 404 */ }
    @Test void cancelConfirmedOrderReturns409() { /* -> 409 */ }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=OrderControllerTest`
Expected: FAIL（404，路由不存在）

- [ ] **Step 3: 实现**

控制器只做 DTO 转换。`ApiExceptionHandler` 用 `@RestControllerAdvice` + `ProblemDetail`：
`NotFoundException → 404`、`InvalidOrderStateException → 409`、`DomainException / IllegalArgumentException → 400`、`OptimisticLockingFailureException → 409`。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=OrderControllerTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(sales): expose order REST API with problem+json errors"
```

---

### Task 9: Inventory 聚合

**Files:**
- Create: `src/main/java/com/example/orders/inventory/domain/{StockItem,Reservation,InsufficientStockException,StockReserved,StockReleased,StockReservationFailed}.java`
- Test: `src/test/java/com/example/orders/inventory/domain/StockItemTest.java`

**Interfaces:**
- Consumes: `Sku`、`OrderId`、`AggregateRoot`
- Produces:
  - `StockItem.create(Sku, String productName, int onHand)`
  - `reserve(OrderId, int quantity)`、`release(OrderId)`、`restock(int quantity)`
  - `available()`、`onHand()`、`reservedFor(OrderId)`

- [ ] **Step 1: 写失败测试**

```java
@Test void reserveReducesAvailable() { /* 10 件，预占 3 -> available 7 */ }
@Test void cannotOverReserve() { /* 10 件，预占 11 -> InsufficientStockException，available 仍 10 */ }
@Test void reserveIsIdempotentPerOrder() { /* 同 order 再预占 3 -> 不重复扣减 */ }
@Test void releaseIsIdempotent() { /* 释放两次 -> available 只加回一次 */ }
@Test void restockRejectsNonPositive() { }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=StockItemTest`
Expected: FAIL

- [ ] **Step 3: 实现**

`reservations` 用 `List<Reservation>`，按 `orderId` 查找；`available()` = `onHand - Σ quantity`。`reserve` 先查已有预占，有则直接返回；否则校验 `available >= quantity` 再追加。`release` 找不到时无操作。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=StockItemTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(inventory): add stock item aggregate with reservations"
```

---

### Task 10: Inventory 持久化与应用服务

**Files:**
- Create: `src/main/java/com/example/orders/inventory/domain/StockItemRepository.java`
- Create: `src/main/java/com/example/orders/inventory/infrastructure/{StockItemRecord,ReservationRecord,StockItemJdbcDao,JdbcStockItemRepository}.java`
- Create: `src/main/java/com/example/orders/inventory/application/{InventoryApplicationService,ReserveLine,ReservationResult,Reserved,StockShortage}.java`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/inventory/application/InventoryApplicationServiceTest.java`

**Interfaces:**
- Consumes: Task 9 的 `StockItem`
- Produces:
  - `InventoryApplicationService.reserve(OrderId, List<ReserveLine>) -> ReservationResult`
  - `release(OrderId)`、`restock(Sku, int)`
  - `ReservationResult` 是 sealed：`Reserved` / `StockShortage(String reason)`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE IF NOT EXISTS inventory_stock_item (
  sku VARCHAR(60) PRIMARY KEY, product_name VARCHAR(200) NOT NULL,
  on_hand INT NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS inventory_reservation (
  order_id UUID NOT NULL, sku VARCHAR(60) NOT NULL, quantity INT NOT NULL,
  PRIMARY KEY (order_id, sku)
);
```

- [ ] **Step 2: 写失败测试**

```java
@DataJdbcTest
@Import({InventoryApplicationService.class, JdbcStockItemRepository.class,
         JdbcOutboxRepository.class, OutboxAppender.class})
class InventoryApplicationServiceTest {
    @Test void reservesAllLinesOrNone() {
        // SKU-A 有 5，SKU-B 有 1；订单要 A:3 B:2 -> StockShortage，且 A 仍为 5
    }
    @Test void successfulReservationWritesIntegrationEvent() { /* outbox 有 stock.reserved.v1 */ }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw -B test -Dtest=InventoryApplicationServiceTest`
Expected: FAIL

- [ ] **Step 4: 实现**

`reserve` 标注 `@Transactional`：先全部加载并检查，全部满足才逐条 `reserve`；任一不足直接返回 `StockShortage`，不修改任何聚合。成功后 `save` 每个聚合并追加 `StockReservedIntegrationEvent`。

- [ ] **Step 5: 运行通过并提交**

Run: `./mvnw -B test -Dtest=InventoryApplicationServiceTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(inventory): add persistence and reservation service"
```

---

### Task 11: Inventory REST 与并发

**Files:**
- Create: `src/main/java/com/example/orders/inventory/application/{InventoryQueryService,InventoryView}.java`
- Create: `src/main/java/com/example/orders/inventory/api/{InventoryController,InventoryResponse}.java`
- Test: `src/test/java/com/example/orders/inventory/ConcurrentReservationTest.java`

**Interfaces:**
- Consumes: Task 10 的 `InventoryApplicationService`
- Produces:
  - `InventoryQueryService.get(Sku) -> InventoryView`（`onHand()`、`reserved()`、`available()`）
  - `GET /api/inventory/{sku}`、`POST /api/inventory/{sku}/stock`

- [ ] **Step 1: 写并发测试**

```java
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentReservationTest {
    @Test void twoOrdersCannotBothTakeLastItem() throws Exception {
        restock("SKU-LAST", 1);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Future<ReservationResult> a = pool.submit(() -> { start.await(); return service.reserve(order1, List.of(new ReserveLine(Sku.of("SKU-LAST"), 1))); });
        Future<ReservationResult> b = pool.submit(() -> { start.await(); return service.reserve(order2, List.of(new ReserveLine(Sku.of("SKU-LAST"), 1))); });
        start.countDown();
        long success = Stream.of(a.get(), b.get()).filter(r -> r instanceof Reserved).count();
        assertThat(success).isEqualTo(1);
        assertThat(inventory.get("SKU-LAST").available()).isZero();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=ConcurrentReservationTest`
Expected: FAIL（`/api/inventory` 不存在或并发断言失败）

- [ ] **Step 3: 实现**

控制器 + 响应 DTO。并发失败由 `@Version` 触发 `OptimisticLockingFailureException`；在 `reserve` 外层加一个非事务包装方法，捕获该异常重试一次，仍失败返回 `StockShortage("并发冲突")`。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=ConcurrentReservationTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(inventory): expose inventory API and verify no oversell under concurrency"
```

---

### Task 12: Billing 聚合

**Files:**
- Create: `src/main/java/com/example/orders/billing/domain/{Invoice,InvoiceId,InvoiceStatus,Payment,PaymentId,PaymentStatus,InvoiceIssued,PaymentCaptured,PaymentFailed,InvoiceVoided}.java`
- Test: `src/test/java/com/example/orders/billing/domain/{InvoiceTest,PaymentTest}.java`

**Interfaces:**
- Consumes: `Money`、`OrderId`、`CustomerId`、`AggregateRoot`
- Produces:
  - `Invoice.issue(InvoiceId, OrderId, CustomerId, Money, Instant)`
  - `Invoice.markPaid(Instant)`、`voidIt(Instant)`、`status()`、`amount()`
  - `Payment.request(PaymentId, InvoiceId, Money, Instant)`
  - `Payment.capture(String gatewayRef, Instant)`、`fail(String reason, Instant)`

- [ ] **Step 1: 写失败测试**

```java
@Test void paidInvoiceCannotBeVoided() { }
@Test void voidedInvoiceCannotBePaid() { }
@Test void capturedPaymentCannotFail() { }
@Test void failedPaymentCannotCapture() { }
@Test void invoiceAmountMustBePositive() { }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest='InvoiceTest,PaymentTest'`
Expected: FAIL

- [ ] **Step 3: 实现**

状态迁移非法时抛 `InvalidOrderStateException` 的兄弟类 `InvalidPaymentStateException`（放 billing.domain）。`Invoice.issue` 校验金额 > 0。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest='InvoiceTest,PaymentTest'`
Expected: PASS

```bash
git add -A && git commit -m "feat(billing): add invoice and payment aggregates"
```

---

### Task 13: 支付网关端口与防腐层

**Files:**
- Create: `src/main/java/com/example/orders/billing/domain/{PaymentGateway,CaptureRequest,CaptureResult,Captured,Declined,TimedOut}.java`
- Create: `src/main/java/com/example/orders/billing/infrastructure/{LegacyPaymentClient,LegacyResponse,SimulatedLegacyPaymentClient,ExternalPaymentGatewayAdapter}.java`
- Test: `src/test/java/com/example/orders/billing/infrastructure/ExternalPaymentGatewayAdapterTest.java`
- Test helper: `src/test/java/com/example/orders/billing/support/FakePaymentGateway.java`

**Interfaces:**
- Consumes: `Money`
- Produces:
  - `PaymentGateway.capture(CaptureRequest) -> CaptureResult`
  - `CaptureRequest(OrderId orderId, Money amount, String idempotencyKey)`
  - `CaptureResult` sealed：`Captured(String gatewayRef)` / `Declined(String reason)` / `TimedOut()`

- [ ] **Step 1: 写失败测试**

```java
@Test void mapsSuccessCode() { assertThat(adapter.capture(req)).isInstanceOf(Captured.class); }
@Test void mapsInsufficientFunds() { /* "51" -> Declined("余额不足") */ }
@Test void mapsTimeout() { /* "91" -> TimedOut */ }
@Test void mapsUnknownCodeToDeclined() {
    // "ZZ" -> Declined，reason 含原始状态码，绝不返回 Captured
}
@Test void convertsYuanToCents() { /* 19.99 CNY -> 1999 */ }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=ExternalPaymentGatewayAdapterTest`
Expected: FAIL

- [ ] **Step 3: 实现**

`SimulatedLegacyPaymentClient` 用可配置的 `statusCode` 返回 `LegacyResponse`。适配器：`amount * 100` 取 `long`（`setScale(0, HALF_UP)`），状态码映射见测试；未知码 → `Declined("未知状态码: " + code)`。
`FakePaymentGateway`（test）实现 `PaymentGateway`，构造时传入队列或固定结果，供后续任务使用。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=ExternalPaymentGatewayAdapterTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(billing): add payment gateway port and anti-corruption adapter"
```

---

### Task 14: Billing 应用服务与持久化

**Files:**
- Create: `src/main/java/com/example/orders/billing/domain/{InvoiceRepository,PaymentRepository}.java`
- Create: `src/main/java/com/example/orders/billing/infrastructure/{InvoiceRecord,PaymentRecord,InvoiceJdbcDao,PaymentJdbcDao,JdbcInvoiceRepository,JdbcPaymentRepository}.java`
- Create: `src/main/java/com/example/orders/billing/application/{BillingApplicationService,BillingResult,Charged,ChargeFailed,BillingQueryService,InvoiceView}.java`
- Create: `src/main/java/com/example/orders/billing/api/InvoiceController.java`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/billing/application/BillingApplicationServiceTest.java`

**Interfaces:**
- Consumes: Task 12/13 的聚合与网关
- Produces:
  - `BillingApplicationService.issueAndCapture(OrderId, CustomerId, Money) -> BillingResult`
  - `BillingResult` sealed：`Charged(InvoiceId, PaymentId)` / `ChargeFailed(String reason)`
  - `BillingQueryService.getByOrder(OrderId) -> Optional<InvoiceView>`，`GET /api/invoices/{orderId}`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE IF NOT EXISTS billing_invoice (
  id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, customer_id UUID NOT NULL,
  amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL, issued_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS billing_payment (
  id UUID PRIMARY KEY, invoice_id UUID NOT NULL, amount DECIMAL(19,2) NOT NULL,
  currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL,
  gateway_ref VARCHAR(120), failure_reason VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL
);
```

- [ ] **Step 2: 写失败测试**

```java
@Test void capturesPaymentAndMarksInvoicePaid() { }
@Test void declinedPaymentVoidsInvoiceAndWritesFailureEvent() { }
@Test void timeoutIsTreatedAsFailure() { /* Payment FAILED reason TIMEOUT */ }
@Test void repeatedCallForSameOrderIsIdempotent() { /* 第二次直接返回已有结果，不重复扣款 */ }
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw -B test -Dtest=BillingApplicationServiceTest`
Expected: FAIL

- [ ] **Step 4: 实现**

`issueAndCapture` 标注 `@Transactional`：按 `orderId` 查已有 Invoice，有则直接返回；否则 issue → request → `gateway.capture` → 按结果 `capture` / `fail` + `voidIt` → 保存 → 写 outbox。

- [ ] **Step 5: 运行通过并提交**

Run: `./mvnw -B test -Dtest=BillingApplicationServiceTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(billing): add billing service with idempotent capture"
```

---

### Task 15: FulfillmentProcess 聚合

**Files:**
- Create: `src/main/java/com/example/orders/fulfillment/domain/{FulfillmentProcess,ProcessId,FulfillmentStatus,InvalidFulfillmentStateException}.java`
- Test: `src/test/java/com/example/orders/fulfillment/domain/FulfillmentProcessTest.java`

**Interfaces:**
- Consumes: `OrderId`、`AggregateRoot`
- Produces:
  - `FulfillmentProcess.start(OrderId, Instant)`
  - `markStockReserved(Instant)`、`markPaid(Instant)`、`complete(Instant)`
  - `startCompensation(String reason, Instant)`、`markCancelled(Instant)`、`markFailed(String reason, Instant)`
  - `status()`、`failureReason()`

- [ ] **Step 1: 写失败测试**

```java
@Test void happyPathTransitions() { STARTED -> STOCK_RESERVED -> PAID -> COMPLETED }
@Test void cannotSkipSteps() { /* STARTED.markPaid -> 异常 */ }
@Test void compensationOnlyFromStockReservedOrPaid() { }
@Test void terminalStatesRejectFurtherTransitions() { }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=FulfillmentProcessTest`
Expected: FAIL

- [ ] **Step 3: 实现**

状态机用 `switch (status)` 显式校验，非法迁移抛 `InvalidFulfillmentStateException`。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=FulfillmentProcessTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(fulfillment): add process aggregate with explicit state machine"
```

---

### Task 16: 消费幂等

**Files:**
- Create: `src/main/java/com/example/orders/sharedkernel/outbox/{ProcessedEventStore,JdbcProcessedEventStore}.java`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/sharedkernel/outbox/ProcessedEventStoreTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `ProcessedEventStore.tryMarkProcessed(UUID eventId, String consumer) -> boolean`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE IF NOT EXISTS processed_event (
  event_id UUID NOT NULL, consumer VARCHAR(120) NOT NULL,
  processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (event_id, consumer)
);
```

- [ ] **Step 2: 写失败测试**

```java
@Test void firstCallReturnsTrueSecondReturnsFalse() { }
@Test void differentConsumerIsIndependent() { }
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw -B test -Dtest=ProcessedEventStoreTest`
Expected: FAIL

- [ ] **Step 4: 实现**

插入用 `INSERT ... ON CONFLICT DO NOTHING` 的 H2 等价写法，或先查后插并捕获主键冲突；返回是否真正插入。

- [ ] **Step 5: 运行通过并提交**

Run: `./mvnw -B test -Dtest=ProcessedEventStoreTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(sharedkernel): add idempotent event consumption"
```

---

### Task 17: Fulfillment 编排——成功路径

**Files:**
- Create: `src/main/java/com/example/orders/fulfillment/domain/FulfillmentProcessRepository.java`
- Create: `src/main/java/com/example/orders/fulfillment/infrastructure/{FulfillmentProcessRecord,FulfillmentProcessJdbcDao,JdbcFulfillmentProcessRepository}.java`
- Create: `src/main/java/com/example/orders/fulfillment/application/port/{InventoryPort,BillingPort,SalesPort,ReservationResult,Reserved,StockShortage,BillingOutcome,Charged,ChargeFailed}.java`
- Create: `src/main/java/com/example/orders/fulfillment/infrastructure/{InventoryPortAdapter,BillingPortAdapter,SalesPortAdapter}.java`
- Create: `src/main/java/com/example/orders/fulfillment/application/{FulfillmentApplicationService,FulfillmentQueryService,FulfillmentView}.java`
- Create: `src/main/java/com/example/orders/fulfillment/api/FulfillmentController.java`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/fulfillment/FulfillmentHappyPathTest.java`

**Interfaces:**
- Consumes: `OrderPlacedIntegrationEvent`、`InventoryApplicationService`、`BillingApplicationService`、`OrderApplicationService`、`ProcessedEventStore`
- Produces:
  - `FulfillmentApplicationService.onOrderPlaced(OrderPlacedIntegrationEvent)`（`@EventListener` + `@Transactional`）
  - `FulfillmentQueryService.get(OrderId) -> FulfillmentView`，`GET /api/fulfillment/{orderId}`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE IF NOT EXISTS fulfillment_process (
  id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, status VARCHAR(30) NOT NULL,
  failure_reason VARCHAR(500), updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL
);
```

- [ ] **Step 2: 写失败测试**

测试里注入 `OrderApplicationService orderApi`、`FulfillmentQueryService fulfillmentApi`、`InventoryQueryService inventoryApi`、`OutboxRelay relay`；`SimulatedLegacyPaymentClient` 默认返回成功状态码 `"00"`。

```java
@SpringBootTest
@ActiveProfiles("test")
class FulfillmentHappyPathTest {
    @Test void orderPlacedRunsThroughToCompletion() {
        OrderId id = orderApi.create(cmd); orderApi.place(id);
        relay.relayOnce();
        assertThat(orderApi.get(id).status()).isEqualTo("CONFIRMED");
        assertThat(fulfillmentApi.get(id).status()).isEqualTo("COMPLETED");
        assertThat(inventoryApi.get("SKU-1").reserved()).isEqualTo(2);
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw -B test -Dtest=FulfillmentHappyPathTest`
Expected: FAIL

- [ ] **Step 4: 实现**

`onOrderPlaced` 先 `tryMarkProcessed(event.eventId(), "fulfillment")`，已处理直接返回；再按 `STARTED → 预占 → PAID → 确认订单 → COMPLETED` 推进，每步保存进度。端口适配器只调用对方**应用服务**，不碰对方 domain。

fulfillment 的 `ReservationResult` / `BillingOutcome` 是它自己的类型，适配器负责把 inventory/billing 的返回值翻译过来——不要直接复用对方 application 包里的类型，这正是上下文边界的体现。

- [ ] **Step 5: 运行通过并提交**

Run: `./mvnw -B test -Dtest=FulfillmentHappyPathTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(fulfillment): orchestrate happy path through ports"
```

---

### Task 18: 补偿路径

**Files:**
- Modify: `src/main/java/com/example/orders/fulfillment/application/FulfillmentApplicationService.java`
- Modify: `src/main/java/com/example/orders/fulfillment/infrastructure/{InventoryPortAdapter,BillingPortAdapter}.java`
- Test: `src/test/java/com/example/orders/fulfillment/FulfillmentCompensationTest.java`

**Interfaces:**
- Consumes: Task 17 的全部
- Produces: 库存不足与支付失败两条补偿路径

- [ ] **Step 1: 写失败测试**

支付失败用例用 `@TestConfiguration` 提供一个 `@Primary` 的 `FakePaymentGateway` bean（队列里放 `Declined` / `TimedOut`）；不要用 `@MockBean`。

```java
@Test void stockShortageCancelsOrder() {
    // 库存只有 1，订单要 2 -> 订单 CANCELLED，流程 FAILED，库存不变
}
@Test void paymentFailureReleasesStockAndCancelsOrder() {
    // FakePaymentGateway 返回 Declined -> 订单 CANCELLED，库存 available 回到原值，流程 CANCELLED
}
@Test void duplicateOrderPlacedEventDoesNotReserveTwice() {
    // 同一事件 relayOnce 两次 -> 只预占一次
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=FulfillmentCompensationTest`
Expected: FAIL

- [ ] **Step 3: 实现**

库存不足：`markFailed(reason)` + `SalesPort.cancel(orderId, reason)`。
支付失败：`startCompensation(reason)` → `InventoryPort.release` → `SalesPort.cancel` → `markCancelled`。
补偿动作各自幂等；重复事件由 `ProcessedEventStore` 挡住。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=FulfillmentCompensationTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(fulfillment): compensate stock and cancel order on failure"
```

---

### Task 19: CQRS 读模型

**Files:**
- Create: `src/main/java/com/example/orders/projections/{OrderSummary,OrderSummaryView,OrderSummaryQuery,JdbcOrderSummaryQuery,OrderSummaryProjector,OrderSummaryRebuilder}.java`
- Modify: `src/main/java/com/example/orders/sales/api/OrderController.java`（`GET /api/orders?customerId=` 改走读模型）
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/example/orders/projections/OrderSummaryProjectorTest.java`

**Interfaces:**
- Consumes: `OrderPlacedIntegrationEvent`、`OrderConfirmedIntegrationEvent`、`OrderCancelledIntegrationEvent`
- Produces: `OrderSummaryQuery.byCustomer(CustomerId) -> List<OrderSummary>`

- [ ] **Step 1: 建表**

```sql
CREATE TABLE IF NOT EXISTS order_summary_view (
  order_id UUID PRIMARY KEY, customer_id UUID NOT NULL, status VARCHAR(20) NOT NULL,
  total_amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
  line_count INT NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- [ ] **Step 2: 写失败测试**

```java
@Test void placedOrderAppearsInReadModel() { /* relayOnce 后 byCustomer 有一条 PLACED */ }
@Test void confirmUpdatesStatusInPlace() { /* 不新增行 */ }
@Test void duplicateEventDoesNotDuplicateRow() { }
@Test void rebuildFromWriteModelRestoresView() { /* 清空投影表 -> rebuilder.rebuild() -> 数量一致 */ }
```

- [ ] **Step 3: 运行确认失败**

Run: `./mvnw -B test -Dtest=OrderSummaryProjectorTest`
Expected: FAIL

- [ ] **Step 4: 实现**

投影器用 `@EventListener` + `ProcessedEventStore`；`rebuild()` 通过 `OrderApplicationService.listAll()` 重放（需要给应用服务加只读方法 `List<OrderView> listAll()`）。

- [ ] **Step 5: 运行通过并提交**

Run: `./mvnw -B test -Dtest=OrderSummaryProjectorTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(projections): add CQRS order summary read model"
```

---

### Task 20: 事件溯源独立示例

**Files:**
- Create: `src/main/java/com/example/orders/examples/eventsourcing/{AccountEvent,AccountOpened,MoneyDeposited,MoneyWithdrawn,Account,AccountId,EventStore,InMemoryEventStore,StoredEvent}.java`
- Test: `src/test/java/com/example/orders/examples/eventsourcing/AccountEventSourcingTest.java`

**Interfaces:**
- Consumes: `Money`（只借用值对象）
- Produces: 独立可运行示例，不接 Spring

- [ ] **Step 1: 写失败测试**

```java
@Test void rehydratesFromEvents() { /* 存 3 个事件 -> 重放后余额正确 */ }
@Test void withdrawBeyondBalanceThrows() { }
@Test void optimisticConcurrencyOnStreamVersion() { /* 版本不符 -> 异常 */ }
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=AccountEventSourcingTest`
Expected: FAIL

- [ ] **Step 3: 实现**

`InMemoryEventStore` 用 `Map<String, List<StoredEvent>>`，`append` 校验 `expectedVersion == list.size()`。`Account.rehydrate(List<AccountEvent>)` 重放。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=AccountEventSourcingTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(examples): add standalone event sourcing example"
```

---

### Task 21: demo profile

**Files:**
- Create: `src/main/java/com/example/orders/bootstrap/{DemoRunner,DemoData}.java`
- Create: `src/main/resources/application-demo.yml`
- Test: `src/test/java/com/example/orders/bootstrap/DemoRunnerTest.java`

**Interfaces:**
- Consumes: 全部应用服务 + `OutboxRelay`
- Produces: `DemoRunner.runScenarios() -> String`（可注入、可在测试中直接调用）；`--spring.profiles.active=demo` 时由 `ApplicationRunner` 调用并打印

- [ ] **Step 1: 写失败测试**

```java
@SpringBootTest
@ActiveProfiles({"test","demo"})
class DemoRunnerTest {
    @Test void printsBothScenarios() {
        String out = demo.runScenarios();
        assertThat(out).contains("OrderPlaced", "StockReserved", "PaymentCaptured", "OrderConfirmed");
        assertThat(out).contains("PaymentFailed", "StockReleased", "OrderCancelled");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw -B test -Dtest=DemoRunnerTest`
Expected: FAIL

- [ ] **Step 3: 实现**

`DemoRunner` 只在 `demo` profile 下作为 `ApplicationRunner` 运行；把核心逻辑抽成静态/可注入的 `runScenarios()` 以便测试。每条动作后调用 `relayOnce()`，最后从 `outbox_event` 读时间线并按 `occurred_at` 排序打印。

- [ ] **Step 4: 运行通过并提交**

Run: `./mvnw -B test -Dtest=DemoRunnerTest`
Expected: PASS

```bash
git add -A && git commit -m "feat(demo): add runnable demo scenarios with event timeline"
```

---

### Task 22: 文档

**Files:**
- Create: `README.md`
- Create: `docs/01-ubiquitous-language.md` … `docs/09-ddd-glossary.md`
- Create: `docs/adr/0001-modular-monolith.md` … `docs/adr/0005-no-event-sourcing-in-main-flow.md`

**Interfaces:**
- Consumes: 已完成的代码
- Produces: 可独立阅读的教学文档

- [ ] **Step 1: 写 README**

包含：项目目的、快速开始（`./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`）、学习路线（按 Task 顺序读哪些包）、目录结构、测试命令。

- [ ] **Step 2: 写 9 篇正文**

每篇要求：先讲概念，再给「本项目在哪」的文件路径，最后给一个反例或常见误解。`04-tactical-patterns.md` 用表格列出概念 → 类名 → 路径。

- [ ] **Step 3: 写 5 篇 ADR**

统一格式：背景 / 决定 / 后果（好的与坏的）/ 替代方案。`0003` 必须写清共享内核的耦合代价；`0005` 必须写清全异步 Saga 与事件溯源的替代方案及不采用的理由。

- [ ] **Step 4: 检查链接**

Run: `grep -rn "](\./" docs README.md | head -50`，逐个确认路径存在。

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "docs: add reading guide, concept map and ADRs"
```

---

### Task 23: 架构规则收口与全量验证

**Files:**
- Create: `src/test/java/com/example/orders/ArchitectureTest.java`
- Test: 全量

**Interfaces:**
- Consumes: 全部代码
- Produces: 可重复执行的架构约束

- [ ] **Step 1: 写架构测试**

```java
@AnalyzeClasses(packages = "com.example.orders")
class ArchitectureTest {
    @ArchTest static final ArchRule domainHasNoFrameworkDeps =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "java.sql..", "jakarta.persistence..", "com.fasterxml..");
    @ArchTest static final ArchRule salesDomainIsIsolated =
        noClasses().that().resideInAPackage("..sales.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..inventory..", "..billing..", "..fulfillment..", "..projections..");
    @ArchTest static final ArchRule inventoryDomainIsIsolated = /* 同上，换包 */;
    @ArchTest static final ArchRule billingDomainIsIsolated = /* 同上 */;
    @ArchTest static final ArchRule fulfillmentDomainIsIsolated = /* 同上 */;
    @ArchTest static final ArchRule domainDoesNotDependOnOuterLayers =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..api..");
    @ArchTest static final ArchRule eventSourcingExampleIsStandalone =
        noClasses().that().resideInAPackage("..examples.eventsourcing..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
}
```

- [ ] **Step 2: 运行架构测试**

Run: `./mvnw -B test -Dtest=ArchitectureTest`
Expected: PASS；若失败，按报错修依赖方向，不要放宽规则

- [ ] **Step 3: 全量测试**

Run: `./mvnw -B verify`
Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 4: 手动跑一遍 demo**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`
Expected: 控制台依次出现成功路径与补偿路径的事件时间线，进程正常退出或保持运行

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "test: enforce architecture boundaries and verify full build"
```

---

## 完成标准

- `./mvnw verify` 全绿
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` 能打印两条路径的时间线
- `Class.forName("org.mockito.Mockito")` 抛 `ClassNotFoundException`
- 架构测试覆盖：领域层无框架依赖、上下文 domain 隔离、示例独立
- `docs/` 下 9 篇正文 + 5 篇 ADR 齐全，链接有效
