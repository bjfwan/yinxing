# 项目路线图

更新时间：2026-04-15

## 1. 当前判断

### 2026-04 当前主线

- 微信自动化主动拨号链路 Phase 1 已完成，稳定性验证通过
- 继续以稳定构建、测试基线和职责收口为核心
- 把桌面主页、应用管理、电话联系人、视频联系人作为当前主干

## 2. 已验证现状

- 在当前工作区于 2026-04-12 直接执行 `.\gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 已验证可通过
- 当前 `lintDebug` 结果已收敛到 `No issues found.`
- 应用管理页已改为基于 `LauncherAppRepository` 读取和缓存桌面应用
- 主页已切到仓储层读取桌面入口，并支持低性能模式刷新策略
- 电话联系人页已下沉为 `PhoneContactRepository` + `PermissionRequestHandler` + `PhoneContactDialogController`
- 视频联系人页已拆出 `VideoCallCoordinator` 和 `VideoContactDialogController`
- 公共空状态已经下沉为 `PageStateView`
- 当前已补单元测试、主干页面 Robolectric UI 冒烟，以及第一批主页、设置、视频联系人页设备级仪器测试脚手架
- **2026-04-15**：微信自动化拨号在 vivo V2285A / Android 15 / 微信 8.0.66 上验证通过；无障碍服务合并为单一服务（`SelectToSpeakService`），用户只需授权一次

## 3. 近期优先级

### 已完成的 P0

- 固化可运行的本地环境配置，统一 `JAVA_HOME` 与 `GRADLE_USER_HOME` 约束
- 把 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug` 固化为验收动作
- 修正文档漂移，让构建状态和文档重新一致

### 已完成的 P1

- 收敛 Android 11+ 应用可见性问题
- 补齐天气入口兜底
- 为电话联系人和视频联系人页补空状态与失败提示
- 收口设置页低性能模式开关

### 已完成的 P2

- 拆分 `PhoneActivity` 的权限申请、联系人读写和对话框逻辑
- 拆分 `VideoCallActivity` 的通话编排与联系人管理对话框逻辑
- 把联系人读写下沉到 `data.contact`
- 把运行时权限流程下沉到 `common.util`
- 补齐 `LauncherPreferences`、`ContactManager`、权限包装和主页/电话联系人页测试
- 清掉当前 `lintDebug` 报告中的全部告警

### 已完成的 P3

- 补 `AppManageActivity`、`SettingsActivity`、`VideoCallActivity` 的 Robolectric UI 冒烟测试
- 补第一批 `androidTest` 设备级脚手架与基础用例
- 重新执行 `:app:assembleDebugAndroidTest`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug` 并通过

### 已完成的 P4（微信自动化 Phase 1）

- 解决 vivo Android 15 微信节点树封锁问题
- 将无障碍服务类名迁移为 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`
- 合并主服务与壳服务为单一无障碍服务，用户只需开启一个授权
- 移除 `packageNames` 限制，避免微信识别为针对性自动化
- 主动拨出完整链路（搜索 → 联系人 → 聊天页 → 音视频通话 → 视频通话）在真实设备验证通过

### 下一阶段 P5

- 微信自动化 Phase 2：来电通知监听与自动接听链路
- 在可用设备上真正执行 `:app:connectedDebugAndroidTest`
- 补设置页系统设置跳转、电话联系人权限拒绝和跨页面仪器测试
- 扩展微信自动化兼容性测试到更多品牌/系统版本

## 4. 暂缓事项

- 自动接听、通知监听（已列入 Phase 2，待排期）
- 远程协助、云同步、日志后台上报
