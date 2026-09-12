# 应用内升级与设备适配观测

更新时间：2026-09-12

## 1. 目标

为银杏后续版本规划两个长期基础能力：

1. 用户在应用内完成版本检查、下载、完整性校验并进入系统安装确认，不再跳转浏览器寻找 APK。
2. 线上日志能按应用构建、安装实例、设备/ROM/微信兼容画像和操作链聚合，支持根据真实用户数据定位设备适配问题。

微信自动化同时修复当前日志已经确认的两个高频边界：

- 系统显示无障碍已启用，但 `SelectToSpeakService` 实例未连接，请求被静默排队直至总超时。
- 已点击视频入口后，华为/微信窗口未暴露既有类名或中文控件，通话成功确认产生假阴性。

成功不等于“自动诊断所有问题”，而是：诊断平台必须明确给出证据充分、数据不足或疑似误报，并允许通过远程兼容配置止损和调整可配置行为。

2026-09-12 发布决策：`2.1.1` 只交付无障碍连接预检、通话确认增强和结构化诊断 v5；应用内下载、远程兼容配置和热更新均不纳入本次发布，现有浏览器整包更新方式保持不变。

## 2. 已确认事实与假设

### 已确认事实

- 当前官网版本清单是 `docs/update.json`，客户端只读取 `versionCode`、`versionName`、`apkUrl`、`releaseNotes`。
- 当前“下载更新”使用浏览器 `ACTION_VIEW`，没有应用内下载、APK 校验或安装结果回传。
- Manifest 已声明 `REQUEST_INSTALL_PACKAGES`，也已有 `FileProvider`。
- 生产日志最近 72 小时的微信失败集中于华为 `BRA-AL00 / Android 12`：
  - `WECHAT_WAITING_HOME_TIMEOUT` 至少 7 次，涉及 5 个安装标识；其中真实日志明确显示“等待无障碍服务连接”。
  - `WECHAT_VERIFYING_CALL_STARTED_TIMEOUT` 横跨历史、聊天、联系人和搜索路线，说明主要失败点在通话成功确认，而不是单一路线入口。
  - 旧版失败样本大量缺少结构化 timeline、构建 SHA 和会话事件序号，无法继续下钻到具体代码与每次动作。

### 实施假设

- APK 继续由项目自己的官网/GitHub Release 分发，并始终使用同一发布签名。
- 允许用户在系统界面确认安装；不做静默安装，不远程加载 Kotlin/DEX/native 代码。
- 远程下发只包含受白名单约束的配置、路由优先级、文本/窗口别名、超时和功能开关。
- 不采集 IMEI、序列号、联系人姓名、聊天内容、截图、录音或原始控件文本。

## 3. 对外契约

### 3.1 更新清单 v2

在现有 `docs/update.json` 上只增加可选字段，旧客户端继续兼容：

```json
{
  "schemaVersion": 2,
  "versionCode": 19,
  "versionName": "2.1.1",
  "apkUrl": "https://example.invalid/app-release.apk",
  "apkSha256": "64 lowercase hex chars",
  "apkSizeBytes": 3000000,
  "packageName": "com.yinxing.launcher",
  "signingCertificateSha256": "64 lowercase hex chars",
  "minimumSupportedVersionCode": 18,
  "mandatory": false,
  "releaseNotes": "..."
}
```

客户端边界校验：HTTPS、允许的主机、字段长度、版本递增、文件大小上限、SHA-256、包名、versionCode 和签名证书必须全部通过，才允许进入安装确认。

### 3.2 微信兼容配置 v1

新增独立、可缓存、可回滚的签名配置：

```json
{
  "schemaVersion": 1,
  "configVersion": 1,
  "expiresAt": "2026-10-01T00:00:00Z",
  "minimumAppVersionCode": 19,
  "killSwitches": { "wechatVideo": false, "historyFastPath": false },
  "timeoutsMs": { "launch": 15000, "home": 15000, "search": 18000, "chat": 20000 },
  "routePriority": ["RECENT_VIDEO_HISTORY", "RECENT_MESSAGES", "CONTACTS", "SEARCH"],
  "callEvidenceAliases": [],
  "windowClassAliases": [],
  "signature": "base64 ECDSA P-256 signature"
}
```

客户端只接受内置白名单字段与范围；签名无效、过期、版本倒退或解析失败时继续使用内置安全默认值。配置不能包含可执行代码、任意选择器脚本或任意 URL。

### 3.3 设备适配事件

所有微信会话事件与失败样本必须包含：

- `build_sha`、`build_type`、`application_id`、`build_source_state`。
- `session_id`、`session_event_sequence`、`trace_id`、`created_at`。
- `manufacturer`、`model`、`sdk_int`、`os_release`、`build_display`、屏幕/密度。
- `wechat_version_name`、`wechat_version_code`。
- `service_setting_enabled`、`service_connected`、`last_accessibility_event_age_ms`。
- `active_package`、`window_class`、`root_available`、脱敏后的节点统计。
- `route`、`capability`、`decision_reason`、`confidence`、`selector_source`。
- 完整且有界的步骤 timeline、最终结果和明确错误码。

服务端增加兼容画像聚合键，按厂商、型号、SDK、ROM build、微信版本、应用构建和路线计算成功率。该键描述兼容环境，不作为永久物理设备标识。

## 4. 项目结构

- `feature.settings.update`：更新清单、下载状态、校验、系统安装交接。
- `common.update`：纯 Kotlin 清单解析、版本策略、哈希/包信息校验策略。
- `automation.wechat.config`：签名兼容配置、缓存、白名单合并和回滚。
- `common.lobster`：设备兼容画像、服务连接状态、投递状态与结构化事件。
- `app/src/test`：解析、策略、服务状态、通话确认和日志契约测试。
- `app/src/androidTest`：从旧版升级、数据保留、安装交接和微信真机故障验证。
- `D:/Desktop/loganalyse/functions/api`：兼容画像聚合与诊断查询，不接收可执行代码。

## 5. 代码风格

用封闭状态表达更新流程，不用多个互相冲突的布尔值：

```kotlin
sealed interface AppUpdateStage {
    data object Idle : AppUpdateStage
    data object Checking : AppUpdateStage
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : AppUpdateStage
    data object Verifying : AppUpdateStage
    data class AwaitingUserInstall(val apkUri: Uri) : AppUpdateStage
    data class Failed(val code: AppUpdateErrorCode) : AppUpdateStage
}
```

所有外部 JSON 在边界解析和验证；内部模块只接收已验证类型。错误码稳定、可聚合，用户文案不作为诊断分组键。

## 6. 测试策略

### 单元测试

- 更新清单 v1/v2 兼容、恶意 URL、版本倒退、超大文件和非法哈希。
- APK 哈希、包名、versionCode、签名证书不匹配必须拒绝。
- 配置签名、过期、回滚、范围限制和安全默认值。
- “已启用但服务未连接”必须产生独立结果，不能排队到 130 秒超时。
- 通话确认结合窗口类、可访问文本/控件和音频状态；单一弱信号不得误判成功。
- 每种失败都生成非空错误码、失败步骤、服务状态、兼容画像和 timeline。

### 集成与真机

- 从已发布 `2.1.0` 检查到 `2.1.1`，在应用内下载并进入系统安装确认。
- 安装完成后联系人、设置和无障碍状态按系统真实行为核验，不能只验证 APK 能打开。
- 断网、下载中断、文件篡改、签名不一致、未知来源权限关闭均有可恢复提示。
- 华为 `BRA-AL00` 复现服务未连接与通话确认两类失败，并确认新错误码和成功率聚合。
- 至少一台非华为设备回归，避免为单机型修复破坏已有路线。

## 7. 实施任务

- [ ] 任务 1：更新清单 v2 契约与纯 Kotlin 解析/策略
  - 验收：v1 兼容，v2 非法输入全部拒绝。
  - 验证：目标单元测试先红后绿。
  - 文件：更新模型、解析器、测试、`docs/update.json`。
- [ ] 任务 2：应用内下载、校验和系统安装交接
  - 验收：内部缓存下载；哈希、包名、版本和签名全部校验；失败可重试。
  - 验证：Robolectric 状态测试、Debug 真机安装交接。
  - 文件：下载器、校验器、安装器、状态测试、FileProvider 配置。
- [ ] 任务 3：更新页面接入与更新事件日志
  - 验收：展示检查、下载进度、校验、权限引导、安装等待和失败原因。
  - 验证：Robolectric UI 流程和人工真机流程。
  - 文件：设置更新控制器、布局/文案、UI 测试、Lobster 更新事件。
- [ ] 任务 4：无障碍连接预检与独立错误码
  - 验收：系统开关已开但 service 未连接时立即提示恢复，不再等待请求 watchdog。
  - 验证：复现测试先红后绿；华为真机验证。
  - 文件：网关、服务连接状态、协调器、测试、日志工厂。
  - 2026-09-12 进度：源码已区分 `DISABLED / ENABLED_NOT_CONNECTED / CONNECTED`，仅在 `onServiceConnected()` 初始化完成后发布在线状态；预检失败使用 `ACCESSIBILITY_ENABLED_NOT_CONNECTED` 上报。目标测试、817 项全量单测和 Debug 构建通过；华为真机仍待验收。
- [ ] 任务 5：通话成功确认改为多信号证据
  - 验收：支持可配置类名/文案，并以两个独立信号或一个强信号连续确认；保留误报保护。
  - 验证：现有确认测试加华为缺失窗口事件回归；真实微信通话验证。
  - 文件：确认器、证据模型、服务调用点、测试、诊断事件。
  - 2026-09-12 进度：已增加“点击前音频为普通模式，点击后进入通信模式”的强信号，并要求连续两次确认；视频选项仍可见、基线缺失或已有系统电话时不得据此成功。可配置别名与华为真机仍待完成。
- [ ] 任务 6：签名微信兼容配置与安全回退
  - 验收：只能调整白名单配置；无效配置绝不影响内置路线。
  - 验证：签名、过期、回滚、kill switch 和缓存测试。
  - 文件：配置模型、验证器、缓存、合并策略、测试。
- [ ] 任务 7：LogAnalyse 设备适配矩阵
  - 验收：按型号/ROM/微信版本/构建/路线展示样本数、成功率、主要失败码和数据质量。
  - 验证：API 合约、D1 查询、前端组件测试和生产只读验证。
  - 文件：迁移、API 查询、类型、组件、测试。

## 8. 边界

### 始终执行

- 保持更新 JSON 向后兼容；发布 APK 必须来自干净提交并归档同次 R8 mapping。
- APK 与远程配置都必须在使用前验证完整性和来源。
- 日志脱敏、有界、可聚合；服务端不得根据设备分布直接宣称因果关系。
- 微信规则必须支持快速关闭和回滚。

### 实施前需要确认

- 是否在 `2.1.1` 发布前完成全部任务 1–7，还是先完成 1–5，把远程配置和矩阵放到下一小版本。
- 官网 APK 与兼容配置最终使用 GitHub Release 还是 Cloudflare 自有存储作为唯一可信源。

### 绝不执行

- 静默安装、绕过 Android 安装确认、远程 DEX/Kotlin/native 代码加载。
- 上传联系人、电话号码、聊天内容、搜索词、截图、录音、视频或完整原始无障碍树。
- 未验证签名/哈希直接安装 APK，或让服务端自由文本直接控制点击动作。

## 9. 完成标准

- 后续启用应用内升级时，旧版到新版的升级链路需在至少两台设备通过，数据没有丢失。
- 真实 2.1.1 日志在生产端显示 clean build SHA、连续事件序号、微信版本、服务连接状态和非空 timeline。
- 华为 BRA-AL00 的“服务未连接”不再表现为泛化的首页超时。
- 通话确认超时能区分“点击未生效”“成功但证据不足”“权限/网络阻断”。
- LogAnalyse 能按兼容画像给出成功率和失败聚类，并明确样本量与证据质量。
