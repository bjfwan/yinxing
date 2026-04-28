# 当前架构说明

更新时间：2026-04-28

## 1. 架构概览

当前项目由 `app` 主应用模块和 `benchmark` 性能测试模块组成，主应用内部按 `feature`、`data`、`common`、`automation` 分层组织。

## 2. 代码分层

### 2.1 feature

- `feature.home`
  - 桌面主页
  - 桌面卡片、拖拽排序、低性能模式刷新逻辑
- `feature.appmanage`
  - 应用管理页
  - 应用列表展示与勾选逻辑
- `feature.phone`
  - 电话联系人页
  - `PhoneContactAdapter`
  - `PhoneContactManager`，使用 `ContactSqliteStore` 的 `phone` 分组
- `feature.videocall`
  - 视频联系人页
  - `VideoCallContactAdapter`
  - `ContactManageAdapter`
  - `VideoCallCoordinator`
  - `VideoContactDialogController`
- `feature.settings`
  - 低性能模式和系统设置入口
- `feature.incoming`
  - 系统电话来电页（实验性）
  - `PhoneCallReceiver`、`IncomingCallForegroundService`、`IncomingNumberMatcher`
  - `IncomingCallRiskAssessor`：来电风险评估（实验性）

### 2.2 data

- `data.home`
  - `LauncherPreferences`
  - `LauncherAppRepository`
  - `HomeAppOrderPolicy`
- `data.contact`
  - `Contact`、`ContactManager`、`ContactSqliteStore`、`ContactStorage`、`ContactAvatarStore`
  - 电话联系人和视频联系人共享 SQLite 数据库，通过 `group_key` 区分 `phone` 与 `wechat`
  - 所有写操作为 `suspend fun`，持久化通过 `Dispatchers.IO.limitedParallelism(1)` 串行化
- `data.weather`
  - `WeatherRepository`：双接口并行加载，`Mutex` 保护缓存原子性

### 2.3 common

- `common.media`
  - 应用图标与 URI 缩略图加载（`MediaThumbnailLoader`）
- `common.service`
  - `TTSService`
- `common.ui`
  - `PageStateView`
  - `FloatingStatusView`
- `common.util`
  - `PermissionRequestHandler`
  - `PermissionUtil`
  - `NetworkUtil`
  - `AccessibilityServiceMatcher`
- `common.ai`
  - `AiGatewayClient`：AI网关客户端
  - `AiProStatusClient`：AI会员状态客户端
  - `AiDeviceCredentials`：AI设备凭据管理

### 2.4 automation

- `automation.wechat`
  - 实验性微信自动化能力
  - 包含状态模型、状态检测、超时管理、无障碍服务和辅助工具
  - `WeChatStepAssistClient`：微信步骤辅助客户端（实验性）

### 2.5 testing

- `app/src/test`
  - 单元测试（171 tests, 0 failed）与 Robolectric UI 冒烟
- `app/src/androidTest`
  - 设备级仪器测试
- `benchmark`
  - Baseline Profile 与 Macrobenchmark

## 3. 数据与存储

- `launcher_prefs`
  - 桌面已选择应用
  - 桌面应用顺序
  - 低性能模式开关
- `launcher_contacts.db`
  - 共享联系人 SQLite 数据库
  - `group_key = "phone"` 存储电话联系人
  - `group_key = "wechat"` 存储视频联系人
- `contacts` / `phone_contacts` / `wechat_contacts`
  - 旧版 SharedPreferences JSON 数据源，仅作为历史迁移兼容边界
- `ContactsContract`
  - 系统通讯录，仅在导入时只读访问

## 4. 外部系统依赖

- Android Launcher 能力
- Contacts Provider
- 系统照片选择器
- 电话拨号权限
- 系统设置页跳转
- 无障碍服务（微信自动化）
- 悬浮窗权限（微信自动化）
- Firebase Crashlytics（崩溃日志上报）

## 5. 当前架构特点

- 没有后端
- 已有轻量 SQLite 联系人数据层，设置和桌面应用配置仍依赖 SharedPreferences
- 没有依赖注入框架
- 主业务仍集中在单个 `app` 模块
- 联系人和天气数据操作全面迁移至后台线程，主线程不做 IO
- 空状态视图和权限流程已经形成公共抽象
- 自动化能力与主业务已做包级隔离，但仍处在同一应用包内
- 测试层已经拆分为单元测试、Robolectric 与设备级仪器测试三层

## 6. 当前仍需关注的点

- `automation.wechat` 仍属于实验性代码，需要继续做设备级稳定性验证
- 系统电话自动接听（`feature.incoming`）兼容性覆盖尚不完整
- 低性能模式、权限拒绝、系统设置跳转和跨页面链路仍缺更完整的设备级回归
- 设置、桌面应用选择和部分状态仍依赖 SharedPreferences，没有更强的状态恢复层

## 7. 当前架构边界

- 主页、应用管理、电话、设置、视频联系人属于当前主业务
- 微信自动化属于实验性能力
- 微信视频来电自动接听、通知监听、远程协助不在当前交付范围内
- 系统电话自动接听已接入实验性链路，稳定性待补齐
