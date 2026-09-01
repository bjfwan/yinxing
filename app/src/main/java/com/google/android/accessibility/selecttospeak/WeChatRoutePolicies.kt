package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames

internal object WeChatListTargetBoundsPolicy {
    fun isSafe(bounds: WeChatUiBounds): Boolean =
        bounds.right > bounds.left &&
            bounds.bottom > bounds.top &&
            bounds.right > 0 &&
            bounds.bottom > 0
}

internal enum class WeChatHomeTabSettleDecision {
    WAIT,
    READY
}

internal object WeChatHomeTabSettlePolicy {
    const val REQUIRED_SELECTED_OBSERVATIONS = 2

    fun decide(
        selected: Boolean,
        consecutiveSelectedObservations: Int
    ): WeChatHomeTabSettleDecision {
        return if (selected && consecutiveSelectedObservations >= REQUIRED_SELECTED_OBSERVATIONS) {
            WeChatHomeTabSettleDecision.READY
        } else {
            WeChatHomeTabSettleDecision.WAIT
        }
    }
}

internal object WeChatHistoryMessageCandidatePolicy {
    private const val COMPOSER_TOP_PERCENT = 93
    private const val HEADER_BOTTOM_PERCENT = 8

    fun chooseLatestVisible(
        rootBounds: WeChatUiBounds,
        candidates: Collection<WeChatUiBounds>
    ): WeChatUiBounds? {
        val height = rootBounds.bottom - rootBounds.top
        if (height <= 0) return null
        val headerBottom = rootBounds.top + height * HEADER_BOTTOM_PERCENT / 100
        val composerTop = rootBounds.top + height * COMPOSER_TOP_PERCENT / 100
        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.left >= rootBounds.left &&
                    candidate.right <= rootBounds.right &&
                    candidate.top >= headerBottom &&
                    candidate.bottom <= composerTop &&
                    candidate.right > candidate.left &&
                    candidate.bottom > candidate.top
            }
            .maxByOrNull { it.bottom }
    }
}

internal object WeChatHistoryCallFallbackPolicy {
    const val FALLBACK_AFTER_MS = 2_200L

    fun shouldFallback(
        historyAttempted: Boolean,
        elapsedMs: Long,
        currentClass: String?,
        callStatus: WeChatCallStartStatus
    ): Boolean {
        return historyAttempted &&
            elapsedMs >= FALLBACK_AFTER_MS &&
            currentClass == WeChatClassNames.CHATTING_UI &&
            callStatus == WeChatCallStartStatus.PENDING
    }
}
