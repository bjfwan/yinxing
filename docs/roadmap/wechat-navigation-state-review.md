# 技术方案评审稿

**文档编号**：TDR-2026-001  
**版本**：v1.0  
**日期**：2026-04-16  
**状态**：待评审  
**作者**：工程团队  
**评审模块**：微信视频拨号 — 导航回退与全局状态管理优化

---

## 一、背景与现状

### 1.1 项目背景

OldLauncher 是一款面向老年用户的 Android Launcher 应用，通过无障碍服务
（`SelectToSpeakService`）实现微信视频拨号的自动化链路。用户在 Launcher
首页点击联系人，系统自动完成：启动微信 → 定位首页 → 搜索联系人 →
进入联系人详情 → 发起视频通话全过程，无需用户手动操作微信。

### 1.2 Phase 1 已完成内容

| 已完成项 | 说明 |
|---|---|
| 稳定的视频拨号主链路 | 在 vivo V2285A / Android 15 / 微信 8.0.66 验证通过 |
| 微信节点树封锁解决 | 服务类名仿冒 Google SelectToSpeak，触发微信白名单 |
| 单服务合并 | 用户只需授权一个无障碍服务 |
| 自适应超时管理 | `TimeoutManager` + `DelayProfile` 动态调整等待时长 |

### 1.3 现存已知问题

本文聚焦 Phase 1 遗留的两个核心设计缺陷，该缺陷在实际使用中已稳定复现：

- **问题一**：从微信内部页面返回首页，最多只能回退 4 步，超出后直接失败，无法直接回到桌面
- **问题二**：整个拨号链路缺乏对外可见的结构化状态，出现异常时只能粗粒度回首页，无法精准恢复到失败前的步骤

---

## 二、问题定义与根因分析

### 2.1 问题一：回退导航能力受限

#### 问题描述

当微信自动化流程进入中间步骤（如搜索页、聊天页、联系人详情页）后，若检测到页面异常需要
回退到微信首页，系统通过连续调用 `performGlobalAction(GLOBAL_ACTION_BACK)` 实现回退。
但最大尝试次数被硬编码为 `MAX_HOME_BACK_ATTEMPTS = 4`，超出上限后流程直接失败，整个
拨号任务终止。

此外，即使回退成功，也仅是回到微信首页，应用层与 Launcher 桌面之间没有任何主动返回桌面
的机制。

#### 代码定位

```
SelectToSpeakService.kt:37
    private const val MAX_HOME_BACK_ATTEMPTS = 4

SelectToSpeakService.kt:803-823
    private fun recoverToHome(...) {
        if (!ensureAttemptBudget(session, "home_back", MAX_HOME_BACK_ATTEMPTS, ...)) {
            return  // 超过4次 → 直接调用 failAndHide，流程终止
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
```

#### 根因分析

| 根因 | 说明 |
|---|---|
| 硬编码次数上限 | `MAX_HOME_BACK_ATTEMPTS = 4` 无法应对微信页面栈深度超过 4 层的场景 |
| 缺少直接回首页能力 | 代码中没有 `GLOBAL_ACTION_HOME`、`moveTaskToBack`、`FLAG_ACTIVITY_CLEAR_TOP` 等直接导航至桌面的手段 |
| 应用层无统一回桌面机制 | `VideoCallActivity`、`SettingsActivity` 等页面均靠 `finish()` 出栈，没有显式跳回 `MainActivity` 的路径 |
| `home_back` 计数未分段 | 同一 key 贯穿整个 `recoverToHome` 调用链，多次调用累积计数后提前触发上限 |

---

### 2.2 问题二：全局状态管理缺失

#### 问题描述

微信拨号链路的内部步骤枚举（`Step`）和页面识别结果（`WeChatPage`）完全封装在
`SelectToSpeakService` 内部，外部模块只能收到三字段的纯文本进度回调：

```kotlin
data class VideoCallProgress(
    val message: String,   // 仅人类可读文案
    val success: Boolean,
    val terminal: Boolean
)
```

外部调用方（`VideoCallCoordinator`、`FloatingStatusView`）无法感知当前处于哪一步，
无法在失败时给出精准的步骤级提示，也无法驱动精准的步骤级恢复策略。

当前异常恢复策略一律为 `rerouteTo(session, Step.WAITING_HOME, ...)` — 即无论在哪个
步骤失败，一律回到首页重新开始，代价高、成功率低。

#### 代码定位

```
SelectToSpeakService.kt:1566-1577（VideoCallSession，仅服务内部可见）
    var step: Step           // 内部步骤状态
    val actionAttempts: MutableMap<String, Int>  // 内部次数计数

SelectToSpeakService.kt:735-740（rerouteTo，强制回首页）
    private fun rerouteTo(session, nextStep: Step, message: String) {
        session.step = nextStep
        session.actionAttempts.clear()   // 清除所有计数，历史信息丢失
    }
```

#### 根因分析

| 根因 | 说明 |
|---|---|
| 状态对外仅暴露字符串 | `VideoCallProgress.message` 是给 TTS 读的文案，不是结构化状态 |
| 已定义的状态枚举未使用 | `AutomationState`（`automation.wechat.model`）定义了完整枚举，但主流程从未引用 |
| 无步骤历史栈 | `actionAttempts` 被 `rerouteTo` 调用时整体清除，失败前的路径信息丢失 |
| 恢复策略粒度粗 | 所有异常路径统一回首页，无法在失败步骤原地重试或回退一步 |
| `StateDetectionManager` 未接入 | 辅助检测类已存在但从未被主流程调用，属于死代码 |

---

## 三、方案设计

> 按优先级由高到低排列，P0 ~ P3。

---

### P0：增加 `GLOBAL_ACTION_HOME` 直接回桌面能力

**目标**：彻底解决"返回不到桌面"的问题，不再依赖连续 BACK 尝试。

#### 技术原理

Android 无障碍服务提供 `GLOBAL_ACTION_HOME`（值 = 2），等效于用户按下物理/虚拟 Home
键。调用后系统直接将前台应用送入后台，Launcher 浮现。此能力不受微信页面栈深度影响，一次
调用即可完成"退出微信、回到桌面"的完整动作，与当前连续 4 次 BACK 的方案相比，既更快、
也更可靠。

#### 具体实现

**Step 1：在 `recoverToHome` 中增加 Home 直跳逻辑**

现有 `recoverToHome` 在 `ensureAttemptBudget` 超限后直接调用 `failAndHide`。
改造为：超限时先尝试 `GLOBAL_ACTION_HOME` 回桌面，再重新启动微信。

```kotlin
// SelectToSpeakService.kt — recoverToHome() 改造示意

private fun recoverToHome(
    session: VideoCallSession,
    root: AccessibilityNodeInfo,
    currentClass: String?,
    reason: String
) {
    updateProgress(session, "正在返回微信首页")
    if (tryDismissTransientUi(session, root)) return

    val backAttempt = (session.actionAttempts["home_back"] ?: 0) + 1
    session.actionAttempts["home_back"] = backAttempt

    // 【新增】超过阈值时改用 Home 键直跳，避免反复 BACK 无效
    if (backAttempt > MAX_HOME_BACK_ATTEMPTS) {
        Log.d(TAG, "recoverToHome: 超过$MAX_HOME_BACK_ATTEMPTS 次，改用 GLOBAL_ACTION_HOME")
        val homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
        if (homeSuccess) {
            // Home 成功 → 重置 session，等待微信被重新拉起
            session.actionAttempts.clear()
            rerouteTo(session, Step.WAITING_HOME, "已回到桌面，正在重新启动微信")
            // 重新启动微信
            launchWeChat()
        } else {
            failAndHide("返回桌面失败，请手动操作", root)
        }
        return
    }

    val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
    Log.d(TAG, "$reason class=$currentClass, backSuccess=$backSuccess attempt=$backAttempt")
    scheduleAdaptiveProcess(session, DelayProfile.RECOVER,
        attemptKey = "home_back", actionSucceeded = backSuccess)
}
```

**Step 2：提高回退上限常量**

将 `MAX_HOME_BACK_ATTEMPTS` 从 4 提高至 6，为常规 BACK 回退留出更大容忍空间。

```kotlin
// 原值
private const val MAX_HOME_BACK_ATTEMPTS = 4
// 修改为
private const val MAX_HOME_BACK_ATTEMPTS = 6
```

**Step 3：`home_back` 计数分段隔离**

`rerouteTo` 进入新步骤时，清除 `home_back` 计数，避免跨步骤累积计数提前触发上限。

```kotlin
private fun rerouteTo(session: VideoCallSession, nextStep: Step, message: String) {
    session.step = nextStep
    session.stepStartedAt = System.currentTimeMillis()
    session.moreButtonClickedAt = 0L
    session.actionAttempts.clear()     // 已有逻辑，确保 home_back 同步清除
}
```

**涉及改动文件**

| 文件 | 改动内容 |
|---|---|
| `SelectToSpeakService.kt` | `recoverToHome()` 增加 Home 跳转分支；`MAX_HOME_BACK_ATTEMPTS` 从 4 改为 6 |

**验收标准**

- 微信页面栈深度 ≥ 5 时，自动化流程不再因 BACK 超限而失败
- `GLOBAL_ACTION_HOME` 成功后，微信被重新拉起，流程从 `WAITING_HOME` 继续
- 正常流程（页面栈 ≤ 6）行为不变

**风险**

- `GLOBAL_ACTION_HOME` 在极少数定制 ROM 上可能无响应，需加兜底检测：调用后
  500ms 内若仍在微信前台，降级为继续 BACK
- 重新拉起微信后，微信可能恢复到之前的状态，需要步骤重置逻辑正确清除缓存

---

### P1：对外暴露结构化步骤状态

**目标**：让 `VideoCallCoordinator`、`FloatingStatusView`、调试工具能感知当前处于
哪个步骤，为 P2 精准恢复提供基础。

#### 技术原理

当前 `VideoCallProgress` 仅有 `message/success/terminal` 三字段，属于面向 TTS
输出的展示层设计。需要在不破坏现有接口的前提下，扩展一个携带步骤枚举的结构化字段。
同时将内部 `Step` 枚举提升为 `public`，配套定义对外状态数据类。

#### 具体实现

**Step 1：将 `Step` 枚举提升为 `public sealed class`（或 `public enum`）**

```kotlin
// 新增对外可见的步骤枚举（可放在独立文件 VideoCallStep.kt）
enum class VideoCallStep {
    IDLE,
    LAUNCHING_WECHAT,       // 对应旧 WAITING_HOME（启动前）
    WAITING_HOME,           // 已启动，等待微信首页就绪
    WAITING_LAUNCHER_UI,    // 在首页，准备搜索/点击联系人
    WAITING_SEARCH,         // 搜索框已打开，输入中
    WAITING_CONTACT_RESULT, // 搜索结果展示，等待点击
    WAITING_CONTACT_DETAIL, // 联系人详情，等待"音视频通话"按钮
    WAITING_VIDEO_OPTIONS,  // 音视频选项弹出，等待点击"视频通话"
    RECOVERING,             // 恢复中（回首页、重试）
    COMPLETED,              // 成功完成
    FAILED                  // 失败终止
}
```

**Step 2：扩展 `VideoCallProgress` 增加 `step` 字段**

```kotlin
data class VideoCallProgress(
    val message: String,
    val success: Boolean,
    val terminal: Boolean,
    val step: VideoCallStep = VideoCallStep.IDLE,  // 【新增】结构化步骤
    val page: String? = null                        // 【新增】当前识别页面（可选，调试用）
)
```

为保持向后兼容，`step` 和 `page` 均有默认值，旧调用代码无需修改。

**Step 3：在 `transitionTo`、`rerouteTo`、`failAndHide`、`notifyState` 处同步填充 `step` 字段**

```kotlin
private fun notifyState(
    session: VideoCallSession,
    state: String,
    success: Boolean,
    terminal: Boolean
) {
    deliverProgress(
        session.requestId,
        VideoCallProgress(
            message = state,
            success = success,
            terminal = terminal,
            step = session.step.toPublicStep()  // 【新增】内部 Step → 外部 VideoCallStep
        )
    )
}

// 映射函数
private fun Step.toPublicStep(): VideoCallStep = when (this) {
    Step.WAITING_HOME          -> VideoCallStep.WAITING_HOME
    Step.WAITING_LAUNCHER_UI   -> VideoCallStep.WAITING_LAUNCHER_UI
    Step.WAITING_SEARCH_FALLBACK -> VideoCallStep.WAITING_SEARCH
    Step.WAITING_CONTACT_RESULT  -> VideoCallStep.WAITING_CONTACT_RESULT
    Step.WAITING_CONTACT_DETAIL  -> VideoCallStep.WAITING_CONTACT_DETAIL
    Step.WAITING_VIDEO_OPTIONS   -> VideoCallStep.WAITING_VIDEO_OPTIONS
}
```

**Step 4：`VideoCallCoordinator` 接入步骤字段，优化 TTS 播报文案**

```kotlin
// VideoCallCoordinator.kt — 根据 step 组合更精准的播报内容
fun onProgress(progress: VideoCallProgress) {
    val ttsMessage = when (progress.step) {
        VideoCallStep.WAITING_SEARCH   -> "正在搜索联系人"
        VideoCallStep.WAITING_CONTACT_RESULT -> "找到联系人，正在点击"
        VideoCallStep.RECOVERING       -> "页面出现异常，正在恢复"
        else -> progress.message
    }
    tts.speak(ttsMessage)
    floatingView?.updateText(progress.message, stepLabel = progress.step.name)
}
```

**涉及改动文件**

| 文件 | 改动内容 |
|---|---|
| `SelectToSpeakService.kt` | `VideoCallProgress` 增加 `step`/`page` 字段；`notifyState` 填充步骤；新增 `Step.toPublicStep()` 映射 |
| `VideoCallCoordinator.kt` | 读取 `progress.step` 优化 TTS 播报逻辑 |
| `FloatingStatusView.kt` | 展示当前步骤标签（可选，调试阶段使用） |
| 新增 `VideoCallStep.kt` | 定义对外公开的步骤枚举 |

**验收标准**

- `VideoCallProgress.step` 在每次回调时准确反映当前内部步骤
- `VideoCallCoordinator` 可按步骤差异化播报 TTS 内容
- 老年用户看到的悬浮窗文案更具描述性（如"正在搜索张三"而非笼统的"正在查找联系人"）

**风险**

- 向后兼容性：所有使用 `VideoCallProgress` 的调用方需检查是否会因新字段引发结构问题；
  Kotlin `data class` 有默认值，理论上安全
- `Step.toPublicStep()` 映射需与 `Step` 枚举保持同步，新增步骤时需一并更新

---

### P2：精准步骤恢复策略（步骤历史栈）

**目标**：异常发生时，不再一律回首页重来，而是根据失败步骤选择最近的安全锚点原地
恢复，减少恢复路径长度，提高拨号成功率。

#### 技术原理

当前 `rerouteTo(session, Step.WAITING_HOME, ...)` 是所有异常的统一出口，代价是放弃
当前搜索结果、重走完整流程。通过引入步骤历史栈和步骤级安全锚点，可以实现：

- 搜索页异常 → 不回首页，直接重新输入搜索词
- 联系人结果页异常 → 不回首页，清除搜索词后重新搜索
- 联系人详情页异常 → 不回首页，重新点击联系人进入

每个步骤对应一个"重试策略"，而非统一的"回首页"策略。

#### 具体实现

**Step 1：在 `VideoCallSession` 中增加步骤历史栈**

```kotlin
private data class VideoCallSession(
    val requestId: String,
    val contactName: String,
    var step: Step,
    var stepStartedAt: Long,
    val startedAt: Long,
    var searchTextApplied: Boolean = false,
    var launcherPrepared: Boolean = false,
    var moreButtonClickedAt: Long = 0L,
    var lastAnnouncedMessage: String? = null,
    val actionAttempts: MutableMap<String, Int> = mutableMapOf(),
    // 【新增】步骤历史栈，记录经过的步骤，用于精准回退
    val stepHistory: ArrayDeque<Step> = ArrayDeque(),
    // 【新增】每步失败次数，超过阈值才向上回退
    val stepFailCount: MutableMap<Step, Int> = mutableMapOf()
)
```

**Step 2：在 `transitionTo` 中记录历史**

```kotlin
private fun transitionTo(session: VideoCallSession, nextStep: Step, message: String) {
    // 记录当前步骤到历史（最多保留最近 5 步，避免无限增长）
    if (session.stepHistory.size >= 5) session.stepHistory.removeFirst()
    session.stepHistory.addLast(session.step)
    
    session.step = nextStep
    session.stepStartedAt = System.currentTimeMillis()
    // ... 原有逻辑
}
```

**Step 3：定义步骤级安全锚点和退回策略**

```kotlin
/**
 * 根据当前失败步骤，决定回退目标。
 * 优先在当前步骤重试；失败次数超阈值时，退回上一个安全锚点。
 */
private fun resolveRecoveryStep(session: VideoCallSession, failedStep: Step): Step {
    val failCount = (session.stepFailCount[failedStep] ?: 0) + 1
    session.stepFailCount[failedStep] = failCount
    
    return when (failedStep) {
        Step.WAITING_VIDEO_OPTIONS -> {
            // 视频选项弹窗消失 → 回联系人详情页重新点击"音视频通话"
            if (failCount <= 2) failedStep else Step.WAITING_CONTACT_DETAIL
        }
        Step.WAITING_CONTACT_DETAIL -> {
            // 进入聊天页但找不到按钮 → 回搜索结果重新进入联系人
            if (failCount <= 2) failedStep else Step.WAITING_CONTACT_RESULT
        }
        Step.WAITING_CONTACT_RESULT -> {
            // 搜索结果为空/无法点击 → 清空重搜
            if (failCount <= 2) Step.WAITING_SEARCH_FALLBACK else Step.WAITING_LAUNCHER_UI
        }
        Step.WAITING_SEARCH_FALLBACK -> {
            // 搜索框打不开 → 回首页重新打开搜索
            Step.WAITING_LAUNCHER_UI
        }
        else -> Step.WAITING_HOME
    }
}
```

**Step 4：将现有 `rerouteTo(WAITING_HOME)` 调用点替换为 `resolveAndRerouteTo`**

```kotlin
private fun resolveAndRerouteTo(
    session: VideoCallSession,
    failedStep: Step,
    reason: String
) {
    val target = resolveRecoveryStep(session, failedStep)
    Log.d(TAG, "resolveAndRerouteTo: failed=$failedStep target=$target reason=$reason")
    rerouteTo(session, target, "正在从${failedStep.name}恢复到${target.name}")
}
```

逐步替换现有代码中直接调用 `rerouteTo(session, Step.WAITING_HOME, ...)` 的位置，
改为调用 `resolveAndRerouteTo(session, session.step, reason)`。

**涉及改动文件**

| 文件 | 改动内容 |
|---|---|
| `SelectToSpeakService.kt` | `VideoCallSession` 新增 `stepHistory`/`stepFailCount`；新增 `resolveRecoveryStep()`/`resolveAndRerouteTo()`；`transitionTo()` 记录历史；替换约 8 处 `rerouteTo(WAITING_HOME)` 调用 |

**验收标准**

- 搜索输入超时（`WAITING_SEARCH_FALLBACK`）：不回首页，直接在搜索页重试
- 搜索结果点击失败（`WAITING_CONTACT_RESULT`）：2 次内重试搜索，3 次后才回首页
- 联系人详情找不到按钮（`WAITING_CONTACT_DETAIL`）：重新进入联系人，不重走搜索
- 完整流程失败率相比 P0 前降低 ≥ 30%（需 A/B 对比数据）

**风险**

- 步骤回退目标若判断错误，可能导致流程在错误页面循环；需为每个步骤设置总失败次数
  上限，超限强制回首页，避免死循环
- `stepHistory` 与 `actionAttempts` 的清除时机需精确控制，防止历史数据干扰新周期

---

### P3：集成 `AutomationState` 与 `StateDetectionManager`，消除死代码

**目标**：将已存在但未接入主流程的 `AutomationState` 枚举和 `StateDetectionManager`
接入实际使用，统一状态语义，降低代码维护成本。

#### 技术原理

项目中 `AutomationState`（`automation.wechat.model`）已定义了 `IDLE`、
`LAUNCHING_WECHAT`、`WAITING_HOME`、`IN_PROGRESS`、`FAILED` 等枚举，与 P1 新增的
`VideoCallStep` 目标高度重叠。`StateDetectionManager` 封装了基于节点树的页面检测逻辑，
与 `detectWeChatPage()` 逻辑存在重叠。此优先级目标是通过一次统一重构，消除这些重复
定义和死代码。

#### 具体实现

**Step 1：评估 `AutomationState` 与 `VideoCallStep`（P1）的合并可行性**

检查 `AutomationState` 的现有字段，确认是否可以直接扩展代替在 P1 中新增
`VideoCallStep`，避免两套枚举并存。若 `AutomationState` 语义更宽泛（适合跨功能模块），
则保持独立；若仅用于微信拨号模块，则合并为一个枚举。

**Step 2：接入 `StateDetectionManager`**

```kotlin
// SelectToSpeakService.kt — detectWeChatPage 委托给 StateDetectionManager
private fun detectWeChatPage(
    root: AccessibilityNodeInfo,
    currentClass: String?
): WeChatPage {
    // 【改造】优先使用 StateDetectionManager 的检测结果
    val detected = stateDetectionManager.detect(root, currentClass)
    if (detected != null) return detected.toWeChatPage()
    
    // 原有逻辑作为兜底
    return detectWeChatPageLegacy(root, currentClass)
}
```

**Step 3：删除或标记废弃的重复逻辑**

- 若 `StateDetectionManager` 检测结果可信，逐步删除 `SelectToSpeakService` 中内联
  的页面检测逻辑（约 50 行），集中维护于 `StateDetectionManager`
- 如检测精度有差异，先用 `@Deprecated` 标记旧逻辑，保留兜底，待验证后再删除

**Step 4：更新文档注释**

为 `AutomationState`、`StateDetectionManager` 补充使用说明和接入指引，防止未来
再次出现"有代码但没人用"的情况。

**涉及改动文件**

| 文件 | 改动内容 |
|---|---|
| `SelectToSpeakService.kt` | `detectWeChatPage()` 委托 `StateDetectionManager`；清理内联检测冗余逻辑 |
| `AutomationState.kt` | 确认语义范围，决定是否合并 `VideoCallStep` |
| `StateDetectionManager.kt` | 补充对外接口文档；确保检测结果与现有 `WeChatPage` 枚举对齐 |

**验收标准**

- `StateDetectionManager` 在主流程中被调用，不再是死代码
- `AutomationState` 或 `VideoCallStep` 统一为一套枚举，不出现两套重叠定义
- 代码 review 中不再出现"这个类是干什么的"的疑问

**风险**

- `StateDetectionManager` 当前可能存在与主流程不一致的检测逻辑，贸然接入可能引入
  新的判断错误；必须先做独立单元测试，对比两种检测方式的准确率
- 重构范围较大，建议在 P0/P1/P2 合并稳定后再启动，避免同时引入多个变动源

---

## 四、优先级与排期

| 优先级 | 方案 | 核心价值 | 改动风险 | 建议排期 |
|---|---|---|---|---|
| **P0** | `GLOBAL_ACTION_HOME` 直跳桌面 | 解决最高频失败场景 | 低（新增分支，不改原有逻辑）| Sprint 1（1 天）|
| **P1** | 对外暴露结构化步骤状态 | 建立可观测性基础 | 低（向后兼容扩展）| Sprint 1（2 天）|
| **P2** | 精准步骤恢复策略 | 显著降低拨号失败率 | 中（涉及状态机核心逻辑）| Sprint 2（3 天）|
| **P3** | 消除死代码，统一状态模型 | 长期可维护性 | 中（重构范围较大）| Sprint 3（2 天）|

> **建议执行顺序**：P0 → P1 → P2 → P3，每个 Sprint 单独提测，避免多个变动同时引入回归风险。

---

## 五、风险汇总与兜底策略

| 风险点 | 影响 | 兜底策略 |
|---|---|---|
| `GLOBAL_ACTION_HOME` 在定制 ROM 上无响应 | P0 方案失效，流程仍然失败 | 调用后 500ms 内检测是否仍在微信，若是则降级继续 BACK |
| 微信版本更新导致 Activity 类名变化 | 页面识别失效，`WeChatPage` 全部返回 `UNKNOWN` | 保留基于节点内容的兜底识别；版本监控告警 |
| 步骤历史栈回退目标判断错误，导致循环 | 流程卡死，永远无法完成拨号 | 为每个步骤设置全局失败次数上限，超限强制 `failAndHide` |
| P1 新字段破坏现有 `VideoCallProgress` 调用方 | 编译错误或逻辑异常 | 所有新字段设置默认值；改动后全量编译验证 |
| P3 接入 `StateDetectionManager` 引入检测差异 | 页面识别准确率下降 | 先单测对比，确认准确率 ≥ 现有方案后再切换；旧逻辑保留为兜底 |

---

## 六、验证设备与测试矩阵

当前已验证设备：

| 设备 | Android 版本 | 微信版本 | 验证状态 |
|---|---|---|---|
| vivo V2285A | Android 15 | 8.0.66 | ✅ 已验证 |

**P0 ~ P2 完成后，建议补充验证**：

| 测试场景 | 验证要点 |
|---|---|
| 微信页面栈深度 = 5、6、7 时发起拨号 | `recoverToHome` 不再因 BACK 超限失败 |
| 网络异常导致搜索超时 | P2 恢复策略正确定位到 `WAITING_SEARCH_FALLBACK` 重试 |
| 联系人不存在（搜索无结果） | `WAITING_CONTACT_RESULT` 正确上报 `terminal=true`，不进入死循环 |
| 拨号中途用户手动切换 App | 流程正确感知并终止，不遗留僵尸 session |
| `GLOBAL_ACTION_HOME` 在 vivo 设备实际效果 | 确认 Home 动作有效，Launcher 浮现 |

---

## 七、附录

### A. 关键类与文件速查

| 类 / 文件 | 路径 | 说明 |
|---|---|---|
| `SelectToSpeakService` | `app/.../selecttospeak/SelectToSpeakService.kt` | 微信自动化主控服务，1597 行 |
| `VideoCallSession` | 同上，第 1566 行 | 单次拨号任务的完整状态持有 |
| `Step` | 同上，第 1588 行 | 内部步骤枚举（6 个步骤） |
| `WeChatPage` | 同上，第 1580 行 | 页面类型枚举（5 种） |
| `VideoCallProgress` | 同上，第 135 行 | 对外状态回调数据类 |
| `recoverToHome()` | 同上，第 803 行 | 返回微信首页逻辑，含 4 次上限 |
| `rerouteTo()` | 同上，第 735 行 | 步骤跳转，清除 `actionAttempts` |
| `AutomationState` | `automation.wechat.model/AutomationState.kt` | 已定义但未使用的状态枚举 |
| `StateDetectionManager` | `automation.wechat.manager/StateDetectionManager.kt` | 已定义但未使用的检测辅助类 |
| `VideoCallCoordinator` | `feature.videocall/VideoCallCoordinator.kt` | 接收回调，驱动 TTS 和悬浮窗 |

### B. 核心常量速查

| 常量 | 当前值 | 作用 |
|---|---|---|
| `MAX_HOME_BACK_ATTEMPTS` | 4 | 最大 BACK 回首页次数，P0 将改为 6 |
| `MAX_UNKNOWN_HOME_OBSERVE_ATTEMPTS` | 2 | 首页未识别页面最大观察次数 |
| `MAX_SEARCH_ENTRY_ATTEMPTS` | 3 | 查找搜索入口最大尝试次数 |
| `MAX_SEARCH_OPEN_ATTEMPTS` | 3 | 打开搜索最大尝试次数 |
| `MAX_CONTACT_DETAIL_ATTEMPTS` | 4 | 联系人详情最大尝试次数 |
| `MAX_VIDEO_OPTION_ATTEMPTS` | 3 | 视频通话选项最大尝试次数 |

---

*文档结束。如有问题，请在 GitHub Issues 中提交或联系工程负责人。*
