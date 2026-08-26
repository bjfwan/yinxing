package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatPackage

internal enum class SearchInputDecision { SUBMIT, WAIT_FOR_VERIFICATION, COMPLETE, RETRY, FAIL }

internal object WeChatSearchInputPolicy {
    const val MAX_FAILED_ATTEMPTS = 5
    const val VERIFICATION_WINDOW_MS = 1_500L

    fun decide(submitted: Boolean, verified: Boolean, elapsedMs: Long, failedAttempts: Int): SearchInputDecision {
        if (!submitted) return if (failedAttempts >= MAX_FAILED_ATTEMPTS) SearchInputDecision.FAIL else SearchInputDecision.SUBMIT
        if (verified) return SearchInputDecision.COMPLETE
        if (elapsedMs < VERIFICATION_WINDOW_MS) return SearchInputDecision.WAIT_FOR_VERIFICATION
        return if (failedAttempts >= MAX_FAILED_ATTEMPTS) SearchInputDecision.FAIL else SearchInputDecision.RETRY
    }
}

internal enum class ForegroundRecoveryDecision { WAIT, RELAUNCH, FAIL }

internal object WeChatForegroundRecoveryPolicy {
    private const val MISSING_ROOT_GRACE_MS = 1_500L
    private const val MAX_RECOVERY_ATTEMPTS = 2

    fun decide(activePackage: String?, missingRootMs: Long, recoveryAttempts: Int): ForegroundRecoveryDecision {
        if (activePackage.isNullOrBlank() || activePackage == WeChatPackage.NAME || isTransientSystemPackage(activePackage)) {
            return ForegroundRecoveryDecision.WAIT
        }
        if (missingRootMs < MISSING_ROOT_GRACE_MS) return ForegroundRecoveryDecision.WAIT
        return if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) ForegroundRecoveryDecision.FAIL else ForegroundRecoveryDecision.RELAUNCH
    }

    private fun isTransientSystemPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return normalized == "com.android.systemui" ||
            normalized.contains("inputmethod") || normalized.contains("keyboard") ||
            normalized.contains(".ime") || normalized.contains("baidu.input")
    }
}
