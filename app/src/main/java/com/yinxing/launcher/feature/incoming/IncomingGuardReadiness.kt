package com.yinxing.launcher.feature.incoming

enum class IncomingGuardItem {
    PhonePermission,
    NotificationPermission,
    DefaultLauncher,
    BatteryOptimization,
    AutoStart,
    BackgroundStart
}

data class IncomingGuardItemState(
    val item: IncomingGuardItem,
    val isReady: Boolean,
    val requiresManualConfirmation: Boolean = false
)

data class IncomingGuardReadiness(
    val items: List<IncomingGuardItemState>
) {
    val blocker: IncomingGuardItemState?
        get() = items.firstOrNull { !it.isReady }

    val completedCount: Int
        get() = items.count { it.isReady }

    val isReady: Boolean
        get() = blocker == null
}

object IncomingGuardReadinessEvaluator {

    fun evaluate(
        hasPhonePermission: Boolean,
        hasNotificationPermission: Boolean,
        isDefaultLauncher: Boolean,
        ignoresBatteryOptimizations: Boolean,
        autoStartConfirmed: Boolean,
        backgroundStartConfirmed: Boolean
    ): IncomingGuardReadiness {
        return IncomingGuardReadiness(
            items = listOf(
                IncomingGuardItemState(
                    item = IncomingGuardItem.PhonePermission,
                    isReady = hasPhonePermission
                ),
                IncomingGuardItemState(
                    item = IncomingGuardItem.NotificationPermission,
                    isReady = hasNotificationPermission
                ),
                IncomingGuardItemState(
                    item = IncomingGuardItem.DefaultLauncher,
                    isReady = isDefaultLauncher
                ),
                IncomingGuardItemState(
                    item = IncomingGuardItem.BatteryOptimization,
                    isReady = ignoresBatteryOptimizations
                ),
                IncomingGuardItemState(
                    item = IncomingGuardItem.AutoStart,
                    isReady = autoStartConfirmed,
                    requiresManualConfirmation = true
                ),
                IncomingGuardItemState(
                    item = IncomingGuardItem.BackgroundStart,
                    isReady = backgroundStartConfirmed,
                    requiresManualConfirmation = true
                )
            )
        )
    }
}

enum class IncomingGuardDiagnosticItem(val label: String) {
    NotificationPermission("通知权限"),
    OverlayPermission("悬浮窗"),
    BackgroundStart("后台启动"),
    Accessibility("无障碍"),
    BatteryOptimization("省电限制"),
    ForegroundService("前台服务")
}

data class IncomingGuardDiagnosticState(
    val item: IncomingGuardDiagnosticItem,
    val isReady: Boolean?
) {
    val statusText: String
        get() = when (isReady) {
            true -> "已完成"
            false -> "待处理"
            null -> "未检测"
        }
}

data class IncomingGuardDiagnosticSnapshot(
    val items: List<IncomingGuardDiagnosticState>
) {
    val failedItems: List<IncomingGuardDiagnosticState>
        get() = items.filter { it.isReady == false }

    fun displayText(): String {
        return items.joinToString(" · ") { "${it.item.label}：${it.statusText}" }
    }
}

object IncomingGuardDiagnosticEvaluator {
    fun evaluate(
        hasNotificationPermission: Boolean,
        canDrawOverlays: Boolean,
        backgroundStartConfirmed: Boolean,
        hasAccessibilityService: Boolean,
        ignoresBatteryOptimizations: Boolean,
        foregroundServiceStarted: Boolean?
    ): IncomingGuardDiagnosticSnapshot {
        return IncomingGuardDiagnosticSnapshot(
            listOf(
                IncomingGuardDiagnosticState(
                    IncomingGuardDiagnosticItem.NotificationPermission,
                    hasNotificationPermission
                ),
                IncomingGuardDiagnosticState(
                    IncomingGuardDiagnosticItem.OverlayPermission,
                    canDrawOverlays
                ),
                IncomingGuardDiagnosticState(
                    IncomingGuardDiagnosticItem.BackgroundStart,
                    backgroundStartConfirmed
                ),
                IncomingGuardDiagnosticState(
                    IncomingGuardDiagnosticItem.Accessibility,
                    hasAccessibilityService
                ),
                IncomingGuardDiagnosticState(
                    IncomingGuardDiagnosticItem.BatteryOptimization,
                    ignoresBatteryOptimizations
                ),
                IncomingGuardDiagnosticState(
                    IncomingGuardDiagnosticItem.ForegroundService,
                    foregroundServiceStarted
                )
            )
        )
    }
}
