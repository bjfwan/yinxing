package com.yinxing.launcher.feature.incoming

import android.os.Build

enum class IncomingCallAcceptStrategy {
    TelecomManager,
    HeadsetHook,
    ManualOnly
}

enum class IncomingCallCompatibilityBlocker {
    ContactWhitelist,
    GlobalAutoAnswer,
    ContactAutoAnswer,
    PhonePermission,
    NotificationPermission,
    DefaultLauncher,
    BatteryOptimization,
    AutoStart,
    BackgroundStart,
    UnsupportedPlatform
}

data class IncomingCallCompatibilityInput(
    val sdkInt: Int,
    val knownContact: Boolean,
    val globalAutoAnswerEnabled: Boolean,
    val contactAutoAnswerEnabled: Boolean,
    val hasPhonePermission: Boolean,
    val hasNotificationPermission: Boolean,
    val isDefaultLauncher: Boolean,
    val ignoresBatteryOptimizations: Boolean,
    val autoStartConfirmed: Boolean,
    val backgroundStartConfirmed: Boolean
)

data class IncomingCallCompatibilityDecision(
    val canAutoAnswer: Boolean,
    val strategy: IncomingCallAcceptStrategy,
    val blockers: List<IncomingCallCompatibilityBlocker>,
    val confidence: Int
) {
    val isReliable: Boolean
        get() = canAutoAnswer && confidence >= 70
}

object IncomingCallCompatibilityDecisionEngine {
    fun decide(input: IncomingCallCompatibilityInput): IncomingCallCompatibilityDecision {
        val blockers = buildList {
            if (!input.knownContact) add(IncomingCallCompatibilityBlocker.ContactWhitelist)
            if (!input.globalAutoAnswerEnabled) add(IncomingCallCompatibilityBlocker.GlobalAutoAnswer)
            if (!input.contactAutoAnswerEnabled) add(IncomingCallCompatibilityBlocker.ContactAutoAnswer)
            if (!input.hasPhonePermission) add(IncomingCallCompatibilityBlocker.PhonePermission)
            if (input.sdkInt >= Build.VERSION_CODES.TIRAMISU && !input.hasNotificationPermission) {
                add(IncomingCallCompatibilityBlocker.NotificationPermission)
            }
            if (!input.isDefaultLauncher) add(IncomingCallCompatibilityBlocker.DefaultLauncher)
            if (!input.ignoresBatteryOptimizations) add(IncomingCallCompatibilityBlocker.BatteryOptimization)
            if (!input.autoStartConfirmed) add(IncomingCallCompatibilityBlocker.AutoStart)
            if (!input.backgroundStartConfirmed) add(IncomingCallCompatibilityBlocker.BackgroundStart)
            if (input.sdkInt < Build.VERSION_CODES.N) add(IncomingCallCompatibilityBlocker.UnsupportedPlatform)
        }
        val strategy = when {
            input.sdkInt >= Build.VERSION_CODES.O -> IncomingCallAcceptStrategy.TelecomManager
            input.sdkInt >= Build.VERSION_CODES.N -> IncomingCallAcceptStrategy.HeadsetHook
            else -> IncomingCallAcceptStrategy.ManualOnly
        }
        val confidence = confidenceFor(strategy, blockers)
        return IncomingCallCompatibilityDecision(
            canAutoAnswer = blockers.isEmpty() && strategy != IncomingCallAcceptStrategy.ManualOnly,
            strategy = if (blockers.contains(IncomingCallCompatibilityBlocker.UnsupportedPlatform)) {
                IncomingCallAcceptStrategy.ManualOnly
            } else {
                strategy
            },
            blockers = blockers,
            confidence = confidence
        )
    }

    private fun confidenceFor(
        strategy: IncomingCallAcceptStrategy,
        blockers: List<IncomingCallCompatibilityBlocker>
    ): Int {
        val base = when (strategy) {
            IncomingCallAcceptStrategy.TelecomManager -> 94
            IncomingCallAcceptStrategy.HeadsetHook -> 66
            IncomingCallAcceptStrategy.ManualOnly -> 0
        }
        val penalty = blockers.sumOf { blocker ->
            when (blocker) {
                IncomingCallCompatibilityBlocker.ContactWhitelist,
                IncomingCallCompatibilityBlocker.GlobalAutoAnswer,
                IncomingCallCompatibilityBlocker.ContactAutoAnswer,
                IncomingCallCompatibilityBlocker.PhonePermission,
                IncomingCallCompatibilityBlocker.UnsupportedPlatform -> 40
                IncomingCallCompatibilityBlocker.NotificationPermission -> 18
                IncomingCallCompatibilityBlocker.DefaultLauncher -> 14
                IncomingCallCompatibilityBlocker.BatteryOptimization,
                IncomingCallCompatibilityBlocker.AutoStart,
                IncomingCallCompatibilityBlocker.BackgroundStart -> 10
            }
        }
        return (base - penalty).coerceIn(0, 100)
    }
}
