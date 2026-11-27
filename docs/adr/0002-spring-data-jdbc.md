# ADR-0002 · 使用 Spring Data JDBC 而非 JPA

- 状态：已采纳
- 日期：2026-11-27

## 背景

本项目按聚合建模，要求：

- 一个聚合一次读写，不依赖懒加载；
- 聚合有明确边界，子表随聚合整体保存/加载；
- 使用乐观锁防止并发覆盖；
- 领域层不依赖持久化框架；
- 数据结构简单，表数量少，查询以主键/唯一键为主。

JPA 的实体身份、脏检查、一级缓存、懒加载与代理机制，会让「聚合边界」与「持久化上下文」纠缠在一起。教学项目希望读者清楚看到「领域对象如何映射到表」，而不是把行为交给框架魔法。

## 决定

使用 **Spring Data JDBC**：

- 每个表一个 `*Record` 类型（如 `OrderRecord`、`OrderLineRecord`），只用于持久化；
- `Jdbc*Repository` 手写领域对象 ↔ 记录映射，聚合整体读写；
- 聚合使用 `@Version` 乐观锁；
- 子表使用 `@MappedCollection`（如 `OrderRecord.lines`、`StockItemRecord.reservations`）；
- Outbox、读模型等非聚合场景直接使用 `JdbcClient` / `JdbcTemplate`；
- 不使用 JPA、Hibernate、Lombok；
- `JdbcMappingConfiguration` 统一覆盖标识符引号策略，修复 Spring Data JDBC 4 默认引用标识符与 H2 未加引号 DDL 的冲突。

## 后果

### 好的

- **聚合边界清晰**：没有懒加载和代理，读者能看到一次 `save`/`findById` 到底读了哪些表。
- **SQL 可预测**：映射由 `Jdbc*Repository` 显式完成，排查问题时不需要理解 Hibernate 的 session 行为。
- **领域层纯净**：`domain` 不 import 任何 Spring Data 类型；`@Table`/`@Id`/`@Version` 只出现在 `infrastructure/*Record`。
- **与手写 SQL 共存自然**：Outbox、投影查询、并发预占的辅助查询直接用 `JdbcClient`。
- **测试简单**：`@DataJdbcTest` + H2 即可覆盖真实映射与乐观锁。

### 坏的

- **映射代码多**：每个聚合都要写 `toRecord` / `toDomain`，字段增减需要同步修改记录、映射、表结构。
- **没有自动关联加载**：跨聚合查询要显式写 SQL 或多次查询；不能依赖 `@ManyToOne` 的便利。
- **复杂查询能力有限**：Spring Data JDBC 的派生查询与分页能力弱于 JPA；复杂读模型最终还是要手写 SQL。
- **生态与资料较少**：遇到问题时社区示例不如 JPA 丰富。
- **`@Version` 需要手动回写**：`save` 后要把数据库生成的新版本写回聚合（`order.assignVersion(saved.version())`），否则同一实例连续保存会冲突。这是本项目在 `Jdbc*Repository` 中显式处理的细节。
- **标识符策略需要额外配置**：Spring Data JDBC 4 默认加引号，与 H2 的大小写折叠冲突，需要 `JdbcMappingConfiguration`。

## 替代方案

| 方案 | 为什么不采用 |
|---|---|
| JPA / Hibernate | 懒加载、脏检查、实体身份会模糊聚合边界；领域对象容易被 `@Entity` 污染；教学重点会被框架细节淹没 |
| 纯 JDBC（JdbcTemplate/JdbcClient） | 最透明，但每个聚合都要手写全部 CRUD 与子表同步；重复代码多，乐观锁也要自己实现 |
| jOOQ | SQL 表达力强、类型安全，但引入代码生成与额外学习成本；对本项目的简单表结构收益有限 |
| MyBatis | SQL 映射灵活，但 XML/注解映射与聚合整体读写的心智模型不如 Spring Data JDBC 直接 |
| 事件溯源持久化 | 主流程不采用，见 [ADR-0005](0005-no-event-sourcing-in-main-flow.md) |

如果未来查询复杂度显著上升，可以保留 Spring Data JDBC 作为写模型，另用 jOOQ 或手写 SQL 构建读模型；这比把写模型迁到 JPA 更符合 CQRS 的方向。
