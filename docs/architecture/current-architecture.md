# 当前架构说明

更新时间：2026-04-12

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
  - `ContactAdapter`
  - `PhoneContactDialogController`
- `feature.videocall`
  - 视频联系人页
  - `VideoCallContactAdapter`
  - `ContactManageAdapter`
  - `VideoCallCoordinator`
  - `VideoContactDialogController`
- `feature.settings`
  - 低性能模式和系统设置入口

### 2.2 data

- `data.home`
  - `LauncherPreferences`
  - `LauncherAppRepository`
  - `HomeAppOrderPolicy`
- `data.contact`
  - `Contact`、`ContactManager`、`ContactStorage`
  - `PhoneContact`、`PhoneContactRepository`

### 2.3 common

- `common.media`
  - 应用图标与 URI 缩略图加载
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

### 2.4 automation

- `automation.wechat`
  - 实验性微信自动化能力
  - 包含状态模型、状态检测、超时管理、无障碍服务和辅助工具

### 2.5 testing

- `app/src/test`
  - 单元测试与 Robolectric UI 冒烟
- `app/src/androidTest`
  - 第一批设备级仪器测试脚手架
- `benchmark`
  - Baseline Profile 与 Macrobenchmark

## 3. 数据与存储

- `launcher_prefs`
  - 桌面已选择应用
  - 桌面应用顺序
  - 低性能模式开关
- `wechat_contacts`
  - 视频联系人本地列表
- `ContactsContract`
  - 电话联系人真实数据源

## 4. 外部系统依赖

- Android Launcher 能力
- Contacts Provider
- 系统照片选择器
- 电话拨号权限
- 系统设置页跳转
- 无障碍服务
- 悬浮窗权限

## 5. 当前架构特点

- 没有后端
- 没有数据库层
- 没有依赖注入框架
- 主业务仍集中在单个 `app` 模块
- 空状态视图和权限流程已经形成公共抽象
- 自动化能力与主业务已做包级隔离，但仍处在同一应用包内
- 测试层已经拆分为单元测试、Robolectric 与设备级仪器测试三层

## 6. 当前仍需关注的点

- `automation.wechat` 仍属于实验性代码，需要继续做设备级稳定性验证
- 第一批仪器测试已搭起脚手架，但当前环境无连接设备，尚未跑通 `connectedDebugAndroidTest`
- 低性能模式、权限拒绝、系统设置跳转和跨页面链路仍缺更完整的设备级回归
- 当前仍主要依赖 SharedPreferences 与系统 Provider，没有更强的状态恢复层

## 7. 当前架构边界

- 主页、应用管理、电话、设置、视频联系人属于当前主业务
- 微信自动化属于实验性能力
- 来电自动接听、通知监听、远程协助不在当前交付范围内
