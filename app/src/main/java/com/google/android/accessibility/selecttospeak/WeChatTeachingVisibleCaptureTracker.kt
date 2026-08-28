package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction

internal class WeChatTeachingVisibleCaptureTracker {
    private var windowClass: String? = null
    private var windowEpoch: Int = 0
    private val capturedEpochByAction = mutableMapOf<WeChatTeachingAction, Int>()

    fun reset() {
        windowClass = null
        windowEpoch = 0
        capturedEpochByAction.clear()
    }

    fun observeWindow(currentWindowClass: String?) {
        if (currentWindowClass != windowClass) {
            windowClass = currentWindowClass
            windowEpoch++
        }
    }

    fun shouldCapture(action: WeChatTeachingAction): Boolean {
        if (capturedEpochByAction[action] == windowEpoch) return false
        capturedEpochByAction[action] = windowEpoch
        return true
    }
}
