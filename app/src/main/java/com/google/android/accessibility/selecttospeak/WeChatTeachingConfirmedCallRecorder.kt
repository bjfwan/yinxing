package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservation
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationKind

internal object WeChatTeachingConfirmedCallRecorder {
    private const val VIDEO_ACTIVITY = "com.tencent.mm.plugin.voip.ui.VideoActivity"

    fun appendIfMissing(
        observations: MutableList<WeChatTeachingObservation>,
        elapsedMs: Long,
        maxSize: Int
    ): Boolean {
        if (observations.size >= maxSize) return false
        if (observations.any { it.kind == WeChatTeachingObservationKind.WINDOW && it.windowClass == VIDEO_ACTIVITY }) {
            return false
        }
        observations += WeChatTeachingObservation(
            kind = WeChatTeachingObservationKind.WINDOW,
            windowClass = VIDEO_ACTIVITY,
            selector = null,
            elapsedMs = elapsedMs.coerceAtLeast(0L)
        )
        return true
    }
}
