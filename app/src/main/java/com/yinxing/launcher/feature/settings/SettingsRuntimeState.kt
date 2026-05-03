package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.feature.incoming.IncomingGuardReadiness
import kotlinx.coroutines.Job

/**
 * [SettingsActivity] 的非视图运行时状态聚合。
 *
 * 之所以单独抽：之前 Activity 顶部混着 16 个视图字段、3 个 Controller、3 个 Launcher，
 * 又夹杂 [incomingGuardReadiness]/[permissionEntryStates]/[contactsSummaryJob] 这种可变模型字段，
 * 阅读时无法快速分辨。把模型字段集中到这里，让 Activity 仅持有 *视图绑定* + *Controller* + *该容器*。
 */
internal class SettingsRuntimeState {
    var incomingGuardReadiness: IncomingGuardReadiness = IncomingGuardReadiness(emptyList())
    var permissionEntryStates: Map<PermissionEntry, PermissionEntryState> = emptyMap()
    var contactsSummaryJob: Job? = null
}
