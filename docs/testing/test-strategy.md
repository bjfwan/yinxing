# 测试策略

更新时间：2026-04-12

## 1. 当前测试现状

- 当前单元测试已覆盖 `HomeAppOrderPolicy`、`ContactStorage`、`LauncherPreferences`、`ContactManager`、`PermissionRequestHandler`
- 当前已补主页、应用管理、设置、电话联系人、视频联系人页的 Robolectric UI 冒烟测试
- 当前已补第一批设备级仪器测试脚手架，覆盖主页、设置页、视频联系人页的基础场景
- 当前命令行验证基线已经固定为 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:lintDebug`

## 2. 当前已验证结果

- 在当前工作区于 2026-04-12 执行 `.\gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 已通过
- 当前 `lintDebug` 结果为 `No issues found.`
- 当前未检测到已连接设备，因此未执行 `:app:connectedDebugAndroidTest`

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

## 6. 第一批仪器测试范围

- 主页启动后能加载出内置入口并展示时间日期
- 设置页切换低性能模式后，摘要文案与偏好值同步更新
- 视频联系人页在空数据时展示空状态
- 视频联系人页在管理模式下可搜索到空结果并清空搜索恢复列表

## 7. 后续自动化测试建议

- 补应用管理页与主页低性能模式的设备级回归断言
- 在可用设备或模拟器上执行 `connectedDebugAndroidTest`，把当前编译通过的仪器测试真正跑起来
- 继续补设置页系统设置跳转、电话联系人权限拒绝、联系人编辑与跨页面跳转场景
- 手工专项测试继续覆盖老年用户操作路径、权限拒绝场景和不同厂商系统兼容性

## 8. 发布门禁建议

- 第一阶段：固定执行 `:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug`
- 第二阶段：触及设备级测试代码时，额外执行 `:app:assembleDebugAndroidTest`
- 第三阶段：接入可用设备后，把 `:app:connectedDebugAndroidTest` 纳入提测门禁
- 第四阶段：继续补齐权限拒绝、跨页面跳转和微信自动化关键路径回归
