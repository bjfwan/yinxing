# 测试策略

更新时间：2026-08-27

## 1. 当前测试分层

- 单元测试覆盖首页排序与偏好、联系人存储、天气数据源与缓存、权限策略、厂商适配和来电决策。
- Robolectric UI 冒烟覆盖主页、应用管理、设置、电话联系人、系统拨号、视频联系人、天气详情和城市管理。
- `app/src/androidTest` 提供设备级仪器测试。
- `benchmark` 提供 Baseline Profile 与 Macrobenchmark。

## 2. 当前验证结果

- 2026-08-27：`testDebugUnitTest` **421 tests, 0 failed**。
- 2026-08-27：`lintDebug` **0 errors, 85 warnings**，`UnusedResources = 0`。
- 2026-08-27：`assembleDebug` 通过。
- 2026-08-27：`assembleRelease` 与 `lintVitalRelease` 通过，Release APK 约 **2.61 MiB**。
- 设置页、天气详情和城市管理已有 vivo V2285A / Android 15 真机设计验收记录，详见根目录 `design-qa.md`。
- 本轮尚未完整重跑 `connectedDebugAndroidTest`，不得将自动化基线写成全量设备验收。

## 3. 最低自动化门禁

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- 触及设备测试时执行 `:app:assembleDebugAndroidTest`
- 发布候选包执行 `:app:assembleRelease` 和 `:app:lintVitalRelease`

## 4. 发布前真机清单

- 默认桌面切换、首页加载、返回键与“调整首页”入口正常。
- 电话、微信视频、天气和家属选择的应用入口正常。
- 应用管理选择、移除、排序和重启恢复正常。
- 电话联系人无权限、空列表、增删改、头像、布局切换和拨号正常。
- 视频联系人管理、搜索、置顶和微信视频前置检查正常。
- 天气定位授权、拒绝、失败回退、城市新增/删除和缓存恢复正常。
- 设置卡片/列表模式、暗色模式、低性能模式、权限入口和版本检查正常。
- 使用真实 SIM 验证系统电话来电、倒计时、接听、挂断、扬声器和联系人匹配。

## 5. 设备级自动化范围

- 主页内置入口、时间日期和主要导航。
- 设置页关键状态和系统设置跳转。
- 电话与视频联系人空状态、交互和跨页面跳转。
- 天气详情、城市管理、返回和定位失败状态。

## 6. 后续补齐

- 在目标设备完整运行 `connectedDebugAndroidTest`。
- 补应用管理和权限拒绝的设备级断言。
- 为系统电话真实通话流程保留人工验收记录，自动化测试不替代运营商与 ROM 兼容性验证。
- 优先处理 Lint 中的无障碍描述、文本资源和高价值性能提示；KTX 与依赖版本建议不阻塞 v2.0.0。

## 7. 历史性能基线

- 2026-04-25：Pixel 8 AVD / Android 14 完成 5 次冷启动 Macrobenchmark；TTID 中位数约 580 ms。
- 已嵌入 Baseline Profile；历史对比中有 Profile 时 P90 帧时间降低约 46%。该结果不是本轮重新测量值。
