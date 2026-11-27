# 01 · 统一语言（Ubiquitous Language）

## 概念

统一语言是团队与代码共用的一套术语。它不是「给术语写注释」，而是要求：

1. 同一个限界上下文内，业务人员、测试、代码使用**同一个词**；
2. 同一个词在不同上下文里可以含义不同，**不要强行统一**；
3. 代码中的类名、方法名、事件名应当直接说业务语言，而不是数据库或框架语言。

判断统一语言是否落地，可以看一个简单问题：**业务人员说「订单已提交」时，代码里能不能找到同名的动作和事件？** 在本项目中是 `Order.place(...)` 与 `OrderPlaced`，而不是 `updateStatus(1)`。

## 本项目在哪

主要术语的落点如下。路径均相对于仓库根目录。

| 中文 | English | 上下文 | 含义 | 代码落点 | 容易混淆的说法（反例） |
|---|---|---|---|---|---|
| 订单 | Order | Sales | 客户的一次购买意向及其生命周期，是 Sales 的聚合根 | `src/main/java/com/example/orders/sales/domain/Order.java` | 不要叫「单据」「记录」；也不要把发票叫订单 |
| 草稿 | DRAFT | Sales | 订单可增删行的初始状态 | `OrderStatus.DRAFT`、`Order.isDraft()` | 不是「未支付」；DRAFT 还没有进入支付流程 |
| 提交订单 / 下单 | Place | Sales | 锁定订单行、计算折扣、进入 `PLACED` 的业务动作 | `Order.place(PricingPolicy, Instant)`、`POST /api/orders/{id}/place` | 不是「支付」，也不是「确认」；提交后还没有扣款 |
| 确认 | Confirm | Sales | 支付成功后由系统把订单推进到 `CONFIRMED` | `Order.confirm(Instant)`、`OrderApplicationService.confirmOrder(...)` | 不是客户动作；不通过公网 `/confirm` 暴露 |
| 取消 | Cancel | Sales | 带原因地把 `DRAFT`/`PLACED` 订单推进到 `CANCELLED` | `Order.cancel(String, Instant)` | 不是删除；取消是状态迁移并产生事件 |
| 订单行 | OrderLine | Sales | 订单中的一项商品快照：SKU、名称、单价、数量 | `sales/domain/OrderLine.java` | 不是库存记录；订单行不负责扣库存 |
| 折扣 | Discount | Sales | 定价策略算出的减免金额，不能为负或超过总额 | `Order.discount()`、`TieredPricingPolicy` | 不是「优惠券」；这里没有优惠券聚合 |
| 应付金额 | Payable | Sales | `total - discount`，不会为负 | `Order.payable()` | 不是「已支付金额」 |
| 库存项 | StockItem | Inventory | 某个 SKU 的库存账本，是 Inventory 的聚合根 | `inventory/domain/StockItem.java` | 不是「商品」；商品名称只是快照字段，商品目录不在本系统 |
| 在库数量 | On hand | Inventory | 实际在库数量 | `StockItem.onHand()` | 不是可用数量 |
| 预占 | Reservation | Inventory | 已被订单占用但尚未出库的数量 | `inventory/domain/Reservation.java`、`StockItem.reserve(...)` | 不是「已售」；预占可以释放 |
| 已预占 | Reserved | Inventory | 某订单当前占用的数量 | `StockItem.reservedFor(OrderId)`、`InventoryView.reserved()` | 不是「已出库」 |
| 可用 | Available | Inventory | `onHand - Σ reservations.quantity` | `StockItem.available()` | 不是「在库」；可用会随预占变化 |
| 释放 | Release | Inventory | 取消某订单的预占，重复释放是幂等的 | `StockItem.release(OrderId, Instant)`、`InventoryApplicationService.release(...)` | 不是「退货入库」；没有产生入库动作 |
| 补货 | Restock | Inventory | 增加在库数量 | `StockItem.restock(int)`、`POST /api/inventory/{sku}/stock` | 不是「预占」；补货不产生领域事件 |
| 发票 | Invoice | Billing | 针对订单的应收凭据，是 Billing 的聚合根 | `billing/domain/Invoice.java` | 不是订单；一个订单最多一张发票 |
| 开具发票 | Issue | Billing | 创建 `ISSUED` 状态的发票 | `Invoice.issue(...)`、`InvoiceIssued` | 不是「支付」；开票不代表扣款成功 |
| 支付 | Payment | Billing | 针对发票的一次支付尝试，是 Billing 的聚合根 | `billing/domain/Payment.java` | 不是「订单」；支付失败不影响订单聚合内部状态，而由 Saga 驱动取消 |
| 捕获 / 扣款 | Capture | Billing | 向支付网关实际发起扣款并得到结果 | `PaymentGateway.capture(CaptureRequest)`、`Payment.capture(...)` | 不是「下单」；Capture 是外部资金动作 |
| 作废 | Void | Billing | 把未支付的发票置为 `VOIDED` | `Invoice.voidIt(Instant)`、`InvoiceVoided` | 不是「退款」；本项目支付失败时作废发票，没有真实退款 |
| 履约流程 | FulfillmentProcess | Fulfillment | 一次订单履约的流程实例，是 Fulfillment 的聚合根 | `fulfillment/domain/FulfillmentProcess.java` | 不是「订单」；它引用 `OrderId`，但不拥有订单规则 |
| 补偿 | Compensation | Fulfillment | 失败后反向执行释放库存、取消订单等动作 | `FulfillmentApplicationService.compensate(...)`、`FulfillmentSteps` | 不是数据库事务回滚；补偿动作各自提交，可能部分成功 |
| 领域事件 | Domain Event | 各上下文 | 聚合内部产生的「已经发生的事实」，只在上下文内消费 | `sales/domain/OrderPlaced.java` 等 | 不是集成事件；不要直接把领域事件发给其他上下文 |
| 集成事件 | Integration Event | 跨上下文 | 稳定的跨上下文契约，经 Outbox 发布 | `sharedkernel/outbox/OrderPlacedIntegrationEvent.java` 等 | 不是领域事件；不要复用聚合内部类型作为契约 |
| 发件箱 | Outbox | 技术/共享 | 与业务写入同事务落库的待发布事件表 | `sharedkernel/outbox/OutboxAppender.java`、`outbox_event` 表 | 不是消息队列；没有 broker |
| 消费者 | Consumer | 技术/共享 | 对某类集成事件做处理的订阅方 | `ProcessedEventStore.tryMarkProcessed(UUID, String)` 中的 `consumer` 参数 | 不是「客户」；客户是 `CustomerId` |
| 读模型 | Read Model | Projections | 为查询优化的投影，例如订单摘要 | `projections/OrderSummary`、`order_summary_view` | 不是写模型；不要用它做业务决策 |

### 同一标识，不同上下文中的含义

`OrderId` 放在共享内核，是因为四个上下文都需要引用同一张订单。但它在每个上下文中的**职责**不同：

- Sales：`Order` 聚合的身份；
- Inventory：`Reservation` 的归属，用来保证同一订单重复预占幂等；
- Billing：`Invoice` 的关联键，一个订单最多一张发票；
- Fulfillment：流程实例关联的业务单据，`FulfillmentProcess` 自己另有 `ProcessId`。

共享的是**标识值**，不是订单聚合。Fulfillment 不会持有 `Order` 对象，只会持有 `OrderId` 并通过 `SalesPort` 调用 Sales 的应用服务。

## 反例与常见误解

### 反例 1：把「提交」叫成「确认」

如果代码写成 `order.confirm()` 表示客户提交订单，那么支付成功后的系统确认就无处安放，最终会出现 `confirm` 与 `payConfirm` 两个含义混乱的方法。本项目把客户动作命名为 `place`，把支付后的系统动作命名为 `confirm`，并在 [ADR 与设计文档](superpowers/specs/2026-11-27-order-management-ddd-design.md) 中明确说明。

### 反例 2：把「预占」说成「已售」

`StockItem.reserve(...)` 只增加 `Reservation`，`onHand` 不变；`release(...)` 可以把预占还回去。如果把预占叫「销售」，补偿路径就会显得像「退货」，业务人员也会误以为库存已经出库。

### 反例 3：跨上下文强行统一术语

Sales 的 `Order` 有状态机与金额规则；Fulfillment 的 `FulfillmentProcess` 也有状态机。它们都叫「状态」，但状态集合完全不同。不要为了「统一」抽一个跨上下文的 `OrderStatus` 给 Fulfillment 使用——本项目的 `FulfillmentStatus` 是独立的枚举。

### 常见误解：统一语言等于写一份术语表

术语表只是结果，不是过程。真正的检查点是：读代码时能否不用翻译就说出业务含义。例如 `StockItem.available()` 比 `getStockCount()` 更接近业务语言；`Order.placedAt()` 比 `getCreateTime()` 更准确。
