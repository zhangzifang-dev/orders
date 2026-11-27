# 02 · 子域与核心域

## 概念

**子域（Subdomain）** 是业务问题空间中的一块领域，不依赖代码组织方式。按对业务竞争力的贡献，通常分为三类：

| 类型 | 含义 | 判断标准 |
|---|---|---|
| 核心域（Core Domain） | 业务真正差异化、值得投入最强设计能力的地方 | 即使外包或买现成产品，也无法获得竞争力 |
| 支撑域（Supporting Subdomain） | 为核心域服务，但业务规则相对通用、可自建 | 需要定制，但不是竞争壁垒 |
| 通用域（Generic Subdomain） | 业界已有成熟方案，通常购买或复用 | 身份认证、审计日志、计费引擎等 |

**限界上下文（Bounded Context）** 是解决方案空间中的边界：一个模型只在边界内保持一致。子域与限界上下文不是一一对应关系，但在教学项目里通常一个子域对应一个上下文，便于观察。

## 本项目在哪

| 上下文 / 模块 | 子域类型 | 核心聚合 | 为什么这样分 | 代码位置 |
|---|---|---|---|---|
| Sales 销售 | **核心域** | `Order` | 订单状态机、金额与折扣规则是整个业务最有判断价值的部分 | `src/main/java/com/example/orders/sales` |
| Inventory 库存 | 支撑域 | `StockItem` | 防超卖与预占规则重要，但模式相对通用，主要为订单履约服务 | `src/main/java/com/example/orders/inventory` |
| Billing 结算 | 支撑域 | `Invoice`、`Payment` | 开票与收款是必要能力，但支付通道通常外购，本项目只保留最小模型 | `src/main/java/com/example/orders/billing` |
| Fulfillment 履约 | 支撑域 | `FulfillmentProcess` | 流程编排与补偿是订单落地的支撑能力，不决定卖什么、怎么定价 | `src/main/java/com/example/orders/fulfillment` |
| 外部支付网关 | **外部系统**，不是子域 | — | 由遗留网关模拟，模型不渗入 Billing 领域 | `billing/infrastructure/LegacyPaymentClient`、`ExternalPaymentGatewayAdapter` |
| sharedkernel | 不是子域 | — | 跨上下文共享的稳定值类型，是技术/建模契约 | `src/main/java/com/example/orders/sharedkernel` |
| projections | 不是子域 | — | 查询侧技术实现，服务于 Sales 的读需求 | `src/main/java/com/example/orders/projections` |

### 为什么 Sales 是核心域

1. **业务判断最密集**：订单从 `DRAFT` 到 `PLACED` 到 `CONFIRMED` 的状态迁移、至少一行、币种一致、折扣边界、取消原因，都是业务规则，而不是技术管道。
2. **规则会变化**：定价策略 `PricingPolicy` 被抽成领域服务，说明折扣规则是可能演进的业务策略；库存与支付的接口则相对稳定。
3. **其他上下文围绕它提供支撑**：Inventory 的预占由订单触发；Billing 的发票金额来自订单应付金额；Fulfillment 的流程围绕订单推进。没有订单，其他上下文没有业务起点。
4. **代码复杂度集中且值得投入**：`Order` 是唯一同时维护行项目、金额、状态机与领域事件的聚合；测试也最密集（`OrderTest`、`OrderStateTest`、`TieredPricingPolicyTest`）。

### 为什么其他三个是支撑域

- **Inventory**：它的统一语言（`onHand`、`reserved`、`available`）与 Sales 不同，但目的很明确——为订单保留库存。防超卖需要认真的并发设计，但这不是本项目的商业差异化。
- **Billing**：需要与外部支付网关打交道，因此重点在**防腐层**，而不是复杂的计费规则。真实系统可能有税率、账期、退款等核心规则；本项目刻意不实现。
- **Fulfillment**：它是流程协调者，规则主要是「先预占、再支付、再确认」以及失败补偿。Saga 很有教学价值，但它服务于订单，不定义卖什么。

### 为什么没有通用域

本项目没有实现身份认证、权限、审计、消息中间件等通用域。这不是遗漏，而是范围控制：它们会引入与 DDD 教学无关的框架和配置。外部支付网关被建模为**外部系统**，用防腐层隔离；它不是一个需要内部建模的通用子域。

## 反例与常见误解

### 误解 1：按技术分层划分子域

把 `controller`、`service`、`repository` 当成子域，是把**解决方案空间**当成了**问题空间**。本项目的顶层包按业务能力划分：`sales`、`inventory`、`billing`、`fulfillment`，每一层内部再分 `domain/application/infrastructure/api`。

### 误解 2：把最复杂的代码当成核心域

Fulfillment 的 Saga 代码可能比 `Order` 更长，但复杂度来自**协调**，不是业务竞争力。核心域的判断标准是「这块规则是否让业务与众不同」，不是「代码行数」。

### 误解 3：核心域必须独立部署

本项目的 Sales 是核心域，但它和其他上下文在同一个 Spring Boot 进程、同一个 Maven 模块中。核心域讲的是**投入与建模优先级**，不是部署单元。为什么选择模块化单体，见 [ADR-0001](adr/0001-modular-monolith.md)。

### 误解 4：共享内核也是一个子域

`sharedkernel` 没有业务行为，也没有独立的业务语言；它只是被多个上下文共同依赖的值类型与基类。把它当子域会导致「为了共享而共享」，最终变成上帝模块。共享内核的代价见 [ADR-0003](adr/0003-shared-kernel-money.md)。
