# OldLauncher 重构进度（progress.md）

> 本文件是对总体重构方案的压缩版与执行视图。
> 任何任务的实际进展都更新到本文件，避免散落在各 PR / 聊天里。
> 修改本文件的优先级高于修改代码——先对齐计划，再写代码。

---

## 0. 目标

一句话：**让 Activity 只显示界面，让数据/规则/设置/网络各回各家，老人能更稳更快地用它。**

四条原则：
- 页面只负责"显示"和"响应点击"，业务逻辑下沉。
- 设置归设置，应用列表归应用列表，不要混在同一个 SharedPreferences 桶里。
- 重构前先补测试；重构后再优化性能。
- 老人友好优先于花哨：看得清、点得准、不晃、不复杂。

---

## 1. Top-5 最先做的事

| 序号 | 事项 | 主要产出 | 验收口径 |
| ---- | ---- | -------- | -------- |
| 1 | 拆 `MainActivity` | `HomeViewModel` / `HomeNavigator` / `TimeTicker` / `WeatherHeaderController` | `MainActivity` 行数 < 200，且不再直接持有 `Handler`/自建 `CoroutineScope` |
| 2 | 补核心单元测试 | 排序、联系人、号码匹配、天气解析、设置读写 | 关键路径测试覆盖率不低于现状，新增至少 6 个 case |
| 3 | 整理设置存储 | 普通设置走 DataStore；应用包名独立存储 | `LauncherPreferences` 不再混存包名作为 key |
| 4 | 强化来电链路可靠性 | 来电状态机 + 诊断日志 | 失败可被诊断为「权限/前台/后台/无障碍」之一 |
| 5 | 继续压启动性能 | 首页首帧优先；其余延后 | 冷启动到首屏可见的时间有可量化的指标 |

---

## 2. 五阶段路线图

| 阶段 | 主题 | 关键产出 | 是否动用户体验 |
| ---- | ---- | -------- | -------------- |
| P0 | 打基础 | 测试、性能埋点、构建/签名清理 | 否 |
| P1 | 拆首页 | `HomeViewModel` 等 + ViewBinding + lifecycleScope | 几乎不变 |
| P2 | 数据层统一 | DataStore 设置、统一 `ContactRepository`、天气分层 | 否（但需要迁移） |
| P3 | 性能与稳定性 | 启动、图标、来电、低性能模式 | 是（更顺更稳） |
| P4 | 工程化 | Gradle 模块拆分、CI、ktlint/detekt、文档 | 否 |

执行节奏：P0 与 P1 可同时启动；P2 等 P0 的"设置层契约"先定；P3、P4 在 P1/P2 落地后再开。

---

## 3. 任务板（可并行）

> 每条任务标注：**[负责区]**（避免改同一个文件互相打架），依赖关系，验收标准。
> 拆分原则：**同一文件只允许一条任务持有写权**。

### A 区：首页与界面（owner_A）

- **A1 拆 `MainActivity` 为 `HomeViewModel` + 协作类**
  - 写权文件：`feature/home/MainActivity.kt`、新增 `feature/home/HomeViewModel.kt`、`feature/home/HomeNavigator.kt`、`feature/home/TimeTicker.kt`、`feature/home/WeatherHeaderController.kt`
  - 依赖：B1 的 `LauncherAppRepository` 接口稳定后落地
  - 验收：`MainActivity` 不再直接 `new CoroutineScope()`、不再用 `Handler.postDelayed`，ViewBinding 替代 `findViewById`
- **A2 首页状态化（Loading / Success / Empty / Error）**
  - 写权文件：`HomeViewModel.kt`、`HomeAppAdapter.kt`、`feature/home/HomeUiState.kt`（新增）
  - 验收：首屏空白态、应用列表加载态、加载失败态可被截图测试或单元测试覆盖
- **A3 RecyclerView 局部刷新**
  - 写权文件：`HomeAppAdapter.kt`、`HomeAppItem.kt`、`ItemMoveCallback.kt`
  - 验收：图标缩放、低性能模式切换不再 `notifyDataSetChanged()`
- **A4 设置页瘦身（按需启动，1304 行不强制本期完成）**
  - 写权文件：`feature/settings/SettingsActivity.kt`，可拆出 `SettingsViewModel`、`SettingsItemAdapter`
  - 验收：单类不超过 600 行；行为不回退

### B 区：数据与设置（owner_B）

- **B1 `LauncherPreferences` 拆桶**
  - 写权文件：`data/home/LauncherPreferences.kt`、新增 `data/home/HomeAppConfig.kt`
  - 拆分目标：
    - 普通设置：低性能模式、深色模式、图标缩放、自动接听、自动接听延迟、全卡片点击、Kiosk、自启确认、后台启动确认 → DataStore（新文件 `data/settings/LauncherSettingsDataStore.kt`）
    - 应用选择：包名集合 + 顺序 → 独立结构 `HomeAppConfig`（保留 SharedPreferences 或独立文件）
  - 强约束：保留旧 `launcher_prefs` 的一次性迁移逻辑，迁移后清空旧 key
  - 验收：`LauncherPreferencesTest` 不退化；新增"旧数据迁移到新结构"测试
- **B2 `LauncherAppRepository` 接口化**
  - 写权文件：`data/home/LauncherAppRepository.kt`、新增 `data/home/LauncherAppSource.kt`（接口）
  - 验收：可注入 `FakeLauncherAppSource` 给 `HomeViewModel` 写测试
- **B3 联系人统一仓库**
  - 写权文件：`data/contact/*`、新增 `data/contact/ContactRepository.kt`（封装 `phone` / `wechat` 分组）
  - 不动文件（其他人写权）：UI 层暂不动
  - 验收：`PhoneContactManager` / `videocall.ContactManageAdapter` 仅依赖 `ContactRepository`
- **B4 联系人 SQLite → Room 迁移评估**
  - 仅产出**调研结论 md**（`docs/contact-room-migration.md`），决定是否动手
  - 验收：写明迁移成本/收益、迁移测试如何兜底；不写代码

### C 区：天气与网络（owner_C）

- **C1 天气源拆分**
  - 写权文件：`data/weather/WeatherRepository.kt`、新增 `data/weather/WeatherApiClient.kt`、`data/weather/source/TencentWeatherDataSource.kt`、`data/weather/source/SeniverseWeatherDataSource.kt`、`data/weather/parser/*`
  - 验收：`WeatherRepository` 不再直接 `HttpURLConnection`/JSON parse；可单测每个 DataSource
- **C2 天气状态机**
  - 写权文件：`data/weather/WeatherState.kt`、`WeatherRepository.kt`
  - 状态：`Loading` / `Success(cache?)` / `Failure(reason)` / `CityNotFound` / `UsingCache`
  - 验收：UI 层（A1 的 `WeatherHeaderController`）只消费状态，不再判断异常类型
- **C3 失败退避 + 磁盘缓存**
  - 写权文件：`WeatherRepository.kt`、新增 `data/weather/WeatherDiskCache.kt`
  - 退避策略：1min → 5min → 15min（封顶）
  - 验收：杀进程后再开 App 仍能看到最近一次成功值；连续失败不打爆接口

### D 区：来电与无障碍（owner_D）

- **D1 来电状态机**
  - 写权文件：`feature/incoming/*`，新增 `feature/incoming/IncomingCallStateMachine.kt`
  - 状态：`Idle` / `Ringing` / `ShowingUi` / `WaitingAutoAnswer` / `Answered` / `Rejected` / `Failed(reason)`
  - 验收：`IncomingCallActivity` 与 `IncomingCallForegroundService` 仅根据状态做事
- **D2 诊断日志强化**
  - 写权文件：`feature/incoming/IncomingCallDiagnostics.kt`、`IncomingGuardReadiness.kt`
  - 输出维度：通知权限 / 悬浮窗 / 后台启动 / 无障碍 / 省电限制 / 前台服务启动结果
  - 验收：失败时可在日志/设置页里看到「失败原因属于哪一类」
- **D3 厂商/版本分支收敛**
  - 写权文件：`feature/incoming/*` 中以 SDK 版本分支的 `if`，集中到一个 `IncomingPlatformCompat`
  - 验收：单元测试可分别 mock Android 10 / 12 / 13 / 14 路径
- **D4 自动接听单测**
  - 写权文件：`app/src/test/java/.../incoming/*`
  - 验收：号码空格、+86、区号、延迟、失败兜底各至少一个 case

### E 区：性能与构建（owner_E）

- **E1 启动埋点**
  - 写权文件：`LauncherApplication.kt`、`feature/home/MainActivity.kt`（仅追加埋点，不改业务）
  - 指标：App 冷启动、首页首帧、应用列表加载、图标加载、天气请求
  - 验收：可被 Macrobenchmark 读取
- **E2 Macrobenchmark 用例补全**
  - 写权文件：`benchmark/`
  - 场景：冷启动、回到桌面、打开应用管理、加载联系人、来电弹窗
- **E3 图标加载优化**
  - 写权文件：`common/media/MediaThumbnailLoader.kt`、`feature/home/HomeAppAdapter.kt`（与 A3 协调）
  - 内容：内存缓存 + 必要磁盘缓存 + bitmap 尺寸控制 + 离屏取消任务
- **E4 构建与签名清理**
  - 写权文件：`app/build.gradle.kts`、`.gitignore`、`README.md`、`docs/release.md`（新增）
  - 必做：
    - 把 `release-key.jks` 从 Git 历史里**评估是否轮换**（独立决策项，先出风险报告）
    - `.gitignore` 显式忽略 `release-key.jks`
    - `local.properties.example` 列出全部需要的 key
    - 写一份 release 流程文档：版本号 → 构建 → 测试 → 发布 → 回滚
- **E5 CI 起步**
  - 写权文件：`.github/workflows/*`（新增）或团队约定的 CI 配置
  - 内容：编译、单测、Lint、ktlint/detekt（先非阻断）

### F 区：测试基线（owner_F，与 A/B/C/D 都协作）

- **F1 排序与首页 ViewModel 测试**
  - 写权文件：`app/src/test/java/.../feature/home/*`
  - 依赖：A1 的 `HomeViewModel` 接口
- **F2 联系人迁移测试**
  - 写权文件：`app/src/test/java/.../data/contact/*`
  - 依赖：B1 的迁移逻辑、B3 的统一仓库
- **F3 号码匹配测试补漏**
  - 写权文件：`feature/incoming/IncomingNumberMatcherTest.kt`
- **F4 天气解析与状态测试**
  - 写权文件：`app/src/test/java/.../data/weather/*`
  - 依赖：C1 / C2

---

## 4. 任务依赖一览

```
P0 ─┬─ E1 启动埋点 ───────────┐
    ├─ E4 构建/签名清理       ├──> P3 性能优化的基线
    └─ F* 关键单测           ─┘

P1 ── A1/A2/A3 拆首页 ──┐
                         ├──> P3 局部优化（图标/低性能模式）
B2 仓库接口化 ───────────┘

B1 设置拆桶 ──┐
              ├──> A1 通过 ViewModel 读到稳定的设置层
              └──> 影响：SettingsActivity（A4）后启动

C1/C2/C3 天气 ──> A1 的 WeatherHeaderController

D1/D2/D3 来电 ──> 与 D4 测试同步推进
```

---

## 5. 协作规范

### 5.1 分支与 PR

- 主干：`main`
- 工作分支命名：`<owner>/<area>/<short-desc>`，例如 `alice/A1/home-viewmodel`
- 单 PR 原则：
  - 只动**自己负责区**列出的写权文件
  - 必带：变更摘要、影响面、测试结论、是否需要数据迁移
  - 行数尽量 < 400；超过先拆
- 不允许跨负责区"顺手改"，需要先在本文件中加一条**协作变更条目**说明

### 5.2 代码风格

- Kotlin 一律用 ktlint 默认规则（先不做强制阻断）
- 不主动加注释/不主动删注释（除非任务本身就是改注释）
- 公共类放 `common/`，跨 feature 的逻辑放 `data/` 或 `domain/`
- 新增文件按现有包结构：
  - 数据：`data/<topic>/`
  - 业务规则（无 Android 依赖）：`domain/<topic>/`（暂未存在，按需新建）
  - 页面：`feature/<screen>/`
  - 工具：`common/<topic>/`

### 5.3 设置/数据迁移规则

- 任何改了存储位置或字段含义的改动：
  - 必须在同一 PR 提供一次性迁移代码
  - 必须新增"旧数据 → 新结构"的单元测试
  - 至少保留**一个版本**的回滚兜底（旧 key 读但不写）

### 5.4 测试要求

- 重构类 PR：必须先有/或同 PR 补上对应单元测试
- 修 bug 类 PR：必须有一个**能复现该 bug 的失败用例**先红再绿
- 不允许仅为了让 CI 过而删除/弱化既有测试

### 5.5 性能与启动

- `LauncherApplication.onCreate()` 里**禁止**新增同步重活
- 首页第一帧之前不做：天气、磁盘 IO、应用列表全量加载、通知通道、应用预热
- 任何启动路径上的新依赖，需要在 PR 描述里说明耗时影响

### 5.6 来电模块

- 任何来电相关改动必须：
  - 经过 `IncomingCallStateMachine`（D1 落地后）
  - 失败有诊断日志类别
  - 至少在 Android 12 / 13 / 14 之中一台真机验证
- 不允许在 UI 层直接做权限判断分支，统一走 `IncomingGuardReadiness`

### 5.7 老人友好底线（PR 自检清单）

- 文案：避免技术术语；权限说明用"为了…请打开…"句式
- 点击区域：交互控件不小于 48dp
- 颜色：状态不能仅靠颜色表达
- 动画：低性能模式下默认关闭进出场动画与阴影
- 字号：不写死 px，引用统一字号资源（待 UI 设计系统落地）

### 5.8 安全与密钥

- `release-key.jks`、`local.properties` 永远不进新提交
- API key 一律 `BuildConfig` + `local.properties`
- 评估天气接口未来通过自建轻量后端代理（远期，不在本期任务）

---

## 6. 进度记录

> 每完成一个任务卡，附在这里。**只记结论**，过程写在 PR 里。

- [x] A1 拆 `MainActivity`
- [x] A2 首页状态化
- [x] A3 RecyclerView 局部刷新
- [x] A4 设置页瘦身
- [x] B1 `LauncherPreferences` 拆桶
- [x] B2 `LauncherAppRepository` 接口化
- [x] B3 联系人统一仓库
- [x] B4 Room 迁移评估
- [x] C1 天气源拆分
- [x] C2 天气状态机
- [x] C3 失败退避 + 磁盘缓存
- [x] D1 来电状态机
- [x] D2 诊断日志强化
- [x] D3 厂商/版本分支收敛
- [x] D4 自动接听单测
- [x] E1 启动埋点
- [x] E2 Macrobenchmark 用例补全
- [x] E3 图标加载优化
- [x] E4 构建与签名清理
- [ ] E5 CI 起步
- [x] F1 排序与首页 ViewModel 测试
- [x] F2 联系人迁移测试
- [x] F3 号码匹配测试补漏
- [x] F4 天气解析与状态测试

---

## 7. 备注

- 模块化（`:core:*` / `:feature:*` 的 Gradle 拆分）属于 P4，本期不做，待包结构稳定后再启动。
- UI 设计系统（统一字号/间距/按钮样式）在本期作为"PR 自检清单"约束落地；正式抽 token 留给独立任务。
