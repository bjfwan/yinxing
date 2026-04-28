# 联系人 SQLite → Room 迁移评估

## 1. 当前现状

当前联系人数据层已经不是 SharedPreferences JSON 直存，而是统一收敛到 `ContactRepository` + `ContactSqliteStore`：

- 电话联系人与视频联系人共用同一个数据库文件：`launcher_contacts.db`
- 通过 `group_key` 区分数据分组：`phone` / `wechat`
- 历史分组名 `phone_contacts` / `wechat_contacts` 已在 `ContactSqliteStore.normalizeGroupKey()` 中兼容
- 当前数据库版本为 `1`
- 当前表结构为单表 `contacts`，主键为复合主键：`(group_key, id)`
- 当前排序和过滤规则已经稳定落在仓库/存储层与 `Contact` 规则里，UI 侧主要消费结果

当前实现的优点是：

- 依赖少，接入成本低
- 读写路径明确，问题定位简单
- 已经满足当前业务：按分组读写、排序、置顶、通话次数、自定义头像、搜索关键词、自动接听字段

## 2. 继续保留自管 SQLite 的收益

在当前阶段继续保留 `ContactSqliteStore` 的收益主要有：

- 不需要新增 Room、KSP/KAPT、schema export 等工程配置
- 避免在 P2 阶段再次触碰联系人持久化主链路，降低回归风险
- 当前库表非常简单，只有单表、单索引、有限查询，手写 SQL 的复杂度仍可控
- `ContactRepository` 已经把调用方隔离开，后续若要换 Room，可以在仓库内平滑替换

## 3. 迁移到 Room 的潜在收益

如果后续联系人能力继续变复杂，Room 会带来以下价值：

- SQL 与字段映射有更强的编译期校验
- schema 变更可以显式写 `Migration`，比手写 `SQLiteOpenHelper` 更规范
- DAO + `Flow` 更适合未来做响应式列表刷新
- 多表关系、分页、复杂查询扩展性更好
- 测试可围绕 DAO 和 migration 做标准化沉淀

## 4. 迁移成本与风险

如果现在就迁移到 Room，主要成本和风险有：

- 需要新增 Room 依赖，以及 KSP 或 KAPT 配置
- 需要决定是否复用现有 `launcher_contacts.db` 与现有表结构
- 需要补 schema 导出目录、版本管理、迁移脚本维护流程
- 当前 `ContactDatabaseHelper.onUpgrade()` 还是空实现，一旦切到 Room，需要补齐从当前 version 1 起步的迁移策略
- 需要额外验证历史数据是否能从现有 SQLite 文件无损迁移
- 需要重新验证联系人排序、置顶、通话次数、搜索关键词、头像 URI、自动接听等字段的兼容性

最大的风险不是“代码写不出来”，而是“已有用户数据迁移不完整或回滚困难”。

## 5. 是否建议现在迁移

**结论：当前阶段不建议立即迁移到 Room。**

理由：

- 当前联系人存储已经从 SharedPreferences JSON 迁到统一 SQLite，收益已经拿到一轮
- 现在的痛点主要不在 ORM，而在功能收口、测试补齐和上层边界稳定
- `ContactRepository` 已经提供了较好的抽象层，后续迁移 Room 时不会强迫 UI 大面积改动
- 当前单表查询简单，Room 的收益还没有大到足以覆盖二次迁移成本

更合适的时机是：

- 联系人表出现第二张及以上关联表
- 需要 `Flow`/响应式数据库监听
- 需要更复杂的筛选、分页、统计查询
- 开始推进 P4 工程化，需要统一 schema 管理与迁移规范

## 6. 如果未来决定迁移，推荐做法

建议采用“仓库接口不变，存储实现替换”的方式：

1. 保持 `ContactRepository` 对外 API 不变
2. 新增 `ContactEntity`、`ContactDao`、`LauncherContactDatabase`
3. 实体字段与当前 `contacts` 表严格一一对应，保留复合主键 `group_key + id`
4. 优先复用现有数据库文件名与表结构，减少一次性数据搬迁成本
5. 先写 Room 读取与写入对照测试，再切正式实现
6. 保留一个版本的回滚窗口：仓库层可切回 `ContactSqliteStore`

## 7. 迁移测试如何兜底

无论是否迁移到 Room，测试兜底至少应覆盖以下内容：

### 7.1 现有回归基线

- `phone` / `wechat` 两个分组彼此隔离
- 排序规则不变：置顶优先、通话次数优先、最后通话时间优先、名称排序兜底
- `incrementCallCount()`、`setPinned()`、`updateContact()`、`removeContact()` 行为不变
- `searchKeywords`、`avatarUri`、`autoAnswer` 字段不丢失

### 7.2 若迁移到 Room，新增测试

- **Migration 测试**：从当前 version 1 数据库升级到 Room 目标版本，校验数据完整性
- **仓库契约测试**：同一组用例同时跑在旧实现与 Room 实现上，结果必须一致
- **异常回滚测试**：迁移失败时应用能给出明确诊断，且不能 silently 清空联系人数据
- **兼容性测试**：验证旧分组别名 `phone_contacts` / `wechat_contacts` 仍能正确归一到 `phone` / `wechat`

## 8. 最终建议

本期建议：

- **不在 B4 直接动手迁移 Room**
- 保留当前 `ContactRepository` + `ContactSqliteStore` 实现
- 先把联系人仓库抽象、测试、调用边界稳定下来
- 等后续出现 schema 演进或响应式查询诉求后，再以独立任务推进 Room

这能在当前阶段以更低风险获得更高确定性，也符合本期“先稳定边界，再做工程化升级”的目标。
