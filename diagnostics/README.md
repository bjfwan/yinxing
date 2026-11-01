# 银杏日志上报链路（源码镜像）

当前镜像对应：银杏 App **v2.5.0**（versionCode 25）·
诊断 `schema_version 5` ·
导出源码提交 `dd2bf18` ·
导出于 2026-11-01T15:32:41.754Z ·
文件数 20

对应安装包 `app-release.apk` 的 SHA-256：

```
3cd2c95077c9c5ed5e599349613facdb03c87addbaff4efe45dee84b21499f03
```

---

## 这个目录是什么

银杏从 v2.4.0 起不再公开新版本源码，只分发 APK。这带来一个合理的问题：**闭源之后，用户凭什么相信"日志上传"这件事说的是真话？**

所以这里公开银杏**日志上报链路**的全部源码，逐字节镜像自发布版本的 App 源码：

| 内容 | 文件 |
|------|------|
| 上报客户端（组包、入队、补传、崩溃记录） | `src/com/yinxing/launcher/common/lobster/LobsterClient.kt` |
| **发送前脱敏** | `src/com/yinxing/launcher/common/lobster/LobsterLogSanitizer.kt` |
| 上报结构定义（schema） | `LobsterStructuredReport.kt`、`LobsterFailureSample.kt`、`LobsterDeviceState.kt` |
| 事件分类与开关策略 | `LobsterEventTaxonomy.kt`、`LobsterRuntimePolicy.kt` |
| 待发送队列 | `LobsterPendingReport.kt` |

范围之外的 App 代码不在这里。这个目录只有一个目的：**让"上报了什么、怎么脱敏的"可被任何人逐行阅读**，不需要相信任何承诺。

### 目录性质（先读这条，避免误会）

- 这是**日志上报链路的镜像**，不是 App 源码重新公开；仓库其余部分的源码仍然停在 v2.3.0。
- 它和 App 源码放在同一个仓，是为了让镜像与版本号天然对齐，不需要额外维护第二个仓库。
- 本目录**不在 GPL-3.0 覆盖范围内**，也不授予任何再分发许可：它是供阅读与核对的透明性材料，不是可复用的开源库。要引用或再分发请先开 issue 沟通。
- 目录内不包含上报地址的访问令牌；`BuildConfig` 里的凭证属于运行时配置，不影响核对上报内容。

---

## 同步规则（硬承诺）

1. **每个 App 版本都对应一次目录更新**，且与 App 版本号同时发布（如 `v2.5.0`）。
2. **链路有改动就必须更新**，并在发布说明里列出改了哪几个文件、为什么改。
3. **链路无改动时也更新**，内容与上一版逐字节相同，只有 `MANIFEST.json` 里的版本号、APK 指纹和导出时间变化——用来证明"这一版的上报代码没被动过"，而不是靠读者自己猜。
4. **改动不会晚于 APK 发布**：先出镜像，再发 APK。顺序反了等于让新包先跑在没人看过的代码上。
5. 每一次发布都在 `MANIFEST.json` 里记录每个文件的 SHA-256、App 版本号、APK 的 SHA-256。任何人都能核对：**手上 APK 的版本号 ↔ 镜像的 `appVersionCode` ↔ 这一堆文件哈希**，三者必须自洽。

关于"一致性"能做到什么程度，说清楚：APK 里的 Kotlin 已被编译成 dex，无法从安装包反推源码，所以**这不是密码学意义上的证明**，而是"发布流程强制校验 + 每版哈希公开可追溯"。如果哪天镜像和实际行为不符，责任在我们，且这种不符是可被抓住的——因为公开的哈希一旦发布就无法悄悄改。

---

## 上报了什么

默认**开启**，可在 App「家属设置 → 诊断日志上报」关闭；关闭后不再入队、不再发送。

**会发送**

- 设备信息：厂商、型号、Android 版本、屏幕参数、字体缩放、可用存储与内存、电池与省电状态、权限授予情况；
- 版本信息：App 版本、微信版本、构建标识；
- 能力事件：走到哪一步、识别到哪类页面、用的哪条路线、失败原因、耗时；
- 脱敏后的日志文本；
- 一个**随机生成的安装 ID**（首次安装时生成，换机或清数据后重新生成，不含任何账号信息）。

**不发送**

- 通讯录、通话记录内容、短信；
- 微信聊天文字、联系人备注、朋友圈、头像；
- 照片、视频、录音、屏幕截图；
- GPS 精确坐标（天气定位失败时的城市名不作为上报字段）；
- 手机号、IMEI、IMSI、MAC、微信账号、任何第三方账号标识。

## 怎么脱敏的

所有进入 `summary`、`logs`、`user_description`、`reproduction_steps`、`steps[].detail` 的文本，发出前都统一过一遍 `LobsterLogSanitizer`（就在本目录里）：

- 手机号 → `138****8000`；
- 固话 → `0571****1234`；
- 邮箱 → `***@***`；
- Bearer 令牌 → `***`；
- `联系人=xxx`、`caller: xxx` 这类带标签的值 → `***`；
- 调用方显式声明的敏感值（如当前联系人姓名、搜索关键词）→ 整体替换为 `***`。

结构化字段（失败样本、设备状态）走的是**白名单**：只允许固定的枚举值、资源 ID 形态、数字区间，其它一律丢弃，不做"先收集再过滤"。

**需要说明的边界**：规则脱敏是对已知形态的匹配，不是语义理解。如果某段自由文本里出现了没有标签、也不符合号码/邮箱形态的人名，单靠规则无法识别。我们的做法是：日志写入端不主动打印联系人姓名、号码和聊天内容（这条约束也在公开代码里能看出一部分）；一旦发现遗漏，**先修脱敏器、同步更新本目录，再发版**，并且历史改动都留在 git 历史里。

---

## 这份镜像不能直接编译

镜像逐字节原样复制，没有为了让它在仓库外编译而改写任何一行代码。它依赖 App 内的三个类：

- `com.yinxing.launcher.BuildConfig`（构建生成，含上报地址与令牌，**不在本目录**——令牌属于运行时凭证，公开它只会让接口被滥用，不影响你核验上报内容）
- `com.yinxing.launcher.common.util.DebugLog`
- `com.yinxing.launcher.data.home.LauncherPreferences`

也就是说：这是一份**可阅读、可核对、可 diff 的源码镜像**，不是能直接 `gradle build` 的独立库。我们不打算为了"看起来完整"而往镜像里塞改写版代码——那反而破坏了一致性。

## 本版文件指纹

- `src/com/yinxing/launcher/common/diagnostics/LauncherDiagnosticReport.kt` — `f56cc51477f784d8`
- `src/com/yinxing/launcher/common/lobster/LobsterAnrEventFactory.kt` — `6a1e2fc178d685ce`
- `src/com/yinxing/launcher/common/lobster/LobsterBuildIdentity.kt` — `fc90c198a735bdf0`
- `src/com/yinxing/launcher/common/lobster/LobsterClient.kt` — `00210add3be79204`
- `src/com/yinxing/launcher/common/lobster/LobsterCrashReporter.kt` — `abb81d16fe197924`
- `src/com/yinxing/launcher/common/lobster/LobsterDeviceIdentity.kt` — `cad3e409c8b22626`
- `src/com/yinxing/launcher/common/lobster/LobsterDeviceState.kt` — `c1a2313a63e0465a`
- `src/com/yinxing/launcher/common/lobster/LobsterEventSequence.kt` — `64ed7fc3c65001d7`
- `src/com/yinxing/launcher/common/lobster/LobsterEventTaxonomy.kt` — `68c9ac9b434784a1`
- `src/com/yinxing/launcher/common/lobster/LobsterFailureSample.kt` — `7a30b0e4bd7a5da3`
- `src/com/yinxing/launcher/common/lobster/LobsterLogSanitizer.kt` — `e371bbc709f63b33`
- `src/com/yinxing/launcher/common/lobster/LobsterMainThreadWatchdog.kt` — `fd9f6be35a893b8f`
- `src/com/yinxing/launcher/common/lobster/LobsterPendingReport.kt` — `f67ecb31c9aff65a`
- `src/com/yinxing/launcher/common/lobster/LobsterProcessExitReporter.kt` — `6d5433a87b263964`
- `src/com/yinxing/launcher/common/lobster/LobsterRuntimePolicy.kt` — `32fa30ad6ffd3921`
- `src/com/yinxing/launcher/common/lobster/LobsterSettingEventFactory.kt` — `30fd434389e31380`
- `src/com/yinxing/launcher/common/lobster/LobsterStructuredReport.kt` — `cc77e3210ffdef2a`
- `src/com/yinxing/launcher/common/lobster/LobsterTrace.kt` — `9160d266bacd2851`
- `src/com/yinxing/launcher/common/lobster/LobsterUsageEvent.kt` — `e45985173ed0cff0`
- `src/com/yinxing/launcher/common/lobster/LobsterUserReportFactory.kt` — `9f47c6f50fca5412`

完整清单见 `MANIFEST.json`。

---

银杏免费、无广告、不设付费功能。做这个镜像不产生任何收入，只为了让"我上传了日志，但我知道它上传了什么"这句话可被验证。
