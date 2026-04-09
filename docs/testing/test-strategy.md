# 测试策略

更新时间：2026-04-09

## 1. 当前测试现状

- 当前单元测试已覆盖 `HomeAppOrderPolicy`、`ContactStorage`、`LauncherPreferences`、`ContactManager`、`PermissionRequestHandler`
- 当前已补主页和电话联系人页的 Robolectric UI 冒烟测试
- 当前命令行验证基线已经固定为 `testDebugUnitTest`、`assembleDebug`、`lintDebug`

## 2. 当前已验证结果

- 在 `JAVA_HOME=D:\android\jbr`、`GRADLE_USER_HOME=D:\gradle-home` 下，`testDebugUnitTest` 已通过
- 在同样环境下，`assembleDebug` 已通过
- 在同样环境下，`lintDebug` 已通过
- 当前 `lintDebug` 结果为 `No issues found.`

## 3. 当前最低验证要求

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- `lintDebug` 通过
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
  - `HomeAppOrderPolicy`
  - `ContactStorage`
  - `LauncherPreferences`
  - `ContactManager`
  - `PermissionRequestHandler`
  - 无障碍服务匹配逻辑
- Robolectric UI 冒烟
  - `MainActivitySmokeTest`
  - `PhoneActivitySmokeTest`

## 6. 后续自动化测试建议

- 补 `VideoCallActivity` 的管理模式和空状态回归测试
- 补 `AppManageActivity` 与 `SettingsActivity` 的 UI 冒烟测试
- 如果后续引入设备或模拟器，再补联系人编辑、权限拒绝和页面跳转的仪器测试
- 手工专项测试继续覆盖老年用户操作路径、权限拒绝场景和不同厂商系统兼容性

## 7. 发布门禁建议

- 第一阶段：固定 `JAVA_HOME` 与 `GRADLE_USER_HOME` 后，强制执行 `assembleDebug` + `testDebugUnitTest` + `lintDebug`
- 第二阶段：补齐视频联系人、设置页和应用管理页的 UI 冒烟测试
- 第三阶段：补齐权限拒绝、跨页面跳转和微信自动化关键路径回归
- 第四阶段：再评估更严格的发布门禁和设备级回归
