package com.yinxing.launcher.feature.settings

import androidx.annotation.StringRes
import com.google.android.accessibility.selecttospeak.WeChatTeachingPrepareResult
import com.yinxing.launcher.R

internal data class WeChatTeachingEntryDecision(
    @param:StringRes val messageRes: Int,
    val openAccessibilitySettings: Boolean = false
)

internal object WeChatTeachingEntryPolicy {
    fun resolve(
        accessibilityEnabled: Boolean,
        prepareResult: WeChatTeachingPrepareResult
    ): WeChatTeachingEntryDecision {
        if (!accessibilityEnabled) {
            return WeChatTeachingEntryDecision(
                messageRes = R.string.settings_wechat_teaching_service_off,
                openAccessibilitySettings = true
            )
        }

        return when (prepareResult) {
            WeChatTeachingPrepareResult.READY -> WeChatTeachingEntryDecision(
                R.string.settings_wechat_teaching_prepared_toast
            )
            WeChatTeachingPrepareResult.SERVICE_NOT_CONNECTED -> WeChatTeachingEntryDecision(
                R.string.settings_wechat_teaching_service_connecting
            )
            WeChatTeachingPrepareResult.OVERLAY_UNAVAILABLE -> WeChatTeachingEntryDecision(
                R.string.settings_wechat_teaching_overlay_failed
            )
            WeChatTeachingPrepareResult.BUSY -> WeChatTeachingEntryDecision(
                R.string.settings_wechat_teaching_busy
            )
            WeChatTeachingPrepareResult.WECHAT_UNAVAILABLE -> WeChatTeachingEntryDecision(
                R.string.settings_wechat_teaching_wechat_missing
            )
        }
    }
}
