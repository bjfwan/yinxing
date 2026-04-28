# 测试策略

更新时间：2026-04-28

## 1. 当前测试现状

- 单元测试覆盖 `HomeAppOrderPolicy`、`ContactStorage`、`LauncherPreferences`、`ContactManager`、`PermissionRequestHandler`
- Robolectric UI 冒烟覆盖主页、应用管理、设置、电话联系人、视频联系人页
- 设备级仪器测试脚手架覆盖主页、设置页、视频联系人页基础场景
- 命令行验证基线固定为 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:lintDebug`

## 2. 当前已验证结果

- 2026-04-28：`testDebugUnitTest` **171 tests, 0 failed**，BUILD SUCCESSFUL
- 2026-04-25：`assembleDebug` 通过
- 2026-04-25：`assembleDebugAndroidTest` 通过
- 2026-04-25：`lintDebug` 通过，`No issues found.`
- 2026-04-25：Macrobenchmark 在模拟器（Pixel 8 AVD / Android 14）上完成 5 次迭代，冷启动 TTID 中位数约 580ms，有 Baseline Profile 时 P90 帧时间 65ms

## 3. 当前最低验证要求

- `:app:assembleDebug` 通过
- `:app:testDebugUnitTest` 通过
- `:app:lintDebug` 通过
- 触及 `androidTest` 时，`:app:assembleDebugAndroidTest` 通过
- 主页可以正常打开并展示内置入口
- 应用管理页可以加载并保存勾选状态
- 电话联系人页可以处理无权限、空列表、联系人增删改和拨号
- 视频联系人页可以在呼叫模式与管理模式间切换，并完成联系人新增、删除和微信视频发起前置检查

## 4. 当前人工测试清单

- 默认桌面切换后，主页入口正常
- 主页返回键提示正常
- 设置入口正常
- 天气入口在有对应厂商应用时可跳转，兜底浏览器可打开
- 电话联系人权限申请正常
- 联系人新增、编辑、删除后页面刷新正常
- 视频联系人新增、删除后列表刷新正常
- 微信视频发起前的网络、无障碍、悬浮窗前置检查正常

## 5. 当前自动化覆盖

- 单元测试
  - `HomeAppOrderPolicyTest`
  - `ContactStorageTest`
  - `LauncherPreferencesTest`
  - `ContactManagerTest`
  - `PermissionRequestHandlerTest`
  - `AccessibilityServiceMatcher` 相关测试
- Robolectric UI 冒烟
  - `MainActivitySmokeTest`
  - `AppManageActivitySmokeTest`
  - `SettingsActivitySmokeTest`
  - `PhoneActivitySmokeTest`
  - `VideoCallActivitySmokeTest`
- 设备级仪器测试
  - `MainActivityInstrumentedTest`
  - `SettingsActivityInstrumentedTest`
  - `VideoCallActivityInstrumentedTest`
- Benchmark / Baseline Profile 分层探针
  - `UiAutomationProbe#launcherUiSmoke`
  - `MacrobenchmarkProbe#coldStartupProbe`
  - `BaselineProfileFrameworkProbe#collectProbe`
  - `BaselineProfileGenerator#generate`

## 6. 设备级仪器测试范围

- 主页启动后能加载出内置入口并展示时间日期
- 主页点击"电话"入口后可进入 `PhoneContactActivity`
- 主页点击"微信视频"入口后可进入 `VideoCallActivity`
- 主页点击天气卡片后可打开天气应用或浏览器天气页
- 设置页切换低性能模式后，摘要文案与偏好值同步更新
- 视频联系人页在空数据时展示空状态
- 视频联系人页在管理模式下可搜索到空结果并清空搜索恢复列表

## 7. 后续自动化测试建议

- 补应用管理页与主页低性能模式的设备级回归断言
- 补设置页系统设置跳转、电话联系人权限拒绝、联系人编辑与跨页面跳转场景
- 在真机上补跑 `connectedDebugAndroidTest` 以验证设备级测试通过率

## 8. 发布门禁

- 第一阶段（当前）：固定执行 `:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug`
- 第二阶段：触及设备级测试代码时，额外执行 `:app:assembleDebugAndroidTest`
- 第三阶段：接入可用设备后，把 `:app:connectedDebugAndroidTest` 纳入提测门禁
- 第四阶段：继续补齐权限拒绝、跨页面跳转和微信自动化关键路径回归

## 9. Benchmark 分层诊断建议

- 当基线采集"看起来卡住"时，先跑 `UiAutomationProbe#launcherUiSmoke`，确认设备解锁、默认桌面与 UI 选择器都正常
- 第二步跑 `MacrobenchmarkProbe#coldStartupProbe`，确认问题是否已发生在 `MacrobenchmarkRule.measureRepeated(...)` 之前/之中
- 第三步跑 `BaselineProfileFrameworkProbe#collectProbe`，专门观察是否能进入 `BaselineProfileRule.collect(...)` 的 lambda
- 最后再跑 `BaselineProfileGenerator#generate`，避免每次都直接进入高成本、低可见性的全量基线采集
