# 当前架构说明

更新时间：2026-04-09

## 1. 架构概览

当前项目是一个单模块 Android 应用，核心结构按 `feature`、`data`、`common`、`automation` 四层整理。

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
  - 联系人展示适配器
  - `PhoneContactDialogController`
- `feature.videocall`
  - 视频联系人页
  - 联系人展示与管理适配器
  - `VideoCallCoordinator`
  - `VideoContactDialogController`
- `feature.settings`
  - 低性能模式和系统设置入口

### 2.2 data

- `data.home`
  - `LauncherPreferences`
  - `LauncherAppRepository`
  - 应用排序策略
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
  - 无障碍服务匹配工具

### 2.4 automation

- `automation.wechat`
  - 实验性微信自动化能力
  - 包含状态模型、超时管理、无障碍服务和无障碍工具

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
- 没有多模块拆分
- 空状态视图和权限流程已经有了公共抽象
- 自动化能力与主业务已做包级隔离，但仍处在同一 app 模块

## 6. 当前仍需关注的点

- `automation.wechat` 仍属于实验性代码，需要继续做设备级稳定性验证
- `AppManageActivity`、`SettingsActivity`、`VideoCallActivity` 的 UI 自动化覆盖还不够
- 当前仍主要依赖 SharedPreferences 与系统 Provider，没有更强的状态恢复层

## 7. 当前架构边界

- 主页、应用管理、电话、设置、视频联系人属于当前主业务
- 微信自动化属于实验性能力
- 来电自动接听、通知监听、远程协助不在当前交付范围内
