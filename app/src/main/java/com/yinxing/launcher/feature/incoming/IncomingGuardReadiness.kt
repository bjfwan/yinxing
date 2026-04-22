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
