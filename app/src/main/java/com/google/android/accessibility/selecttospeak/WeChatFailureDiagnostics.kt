package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.DebugLog

internal data class WeChatFailureSnapshot(
    val step: String,
    val contactName: String,
    val startedAt: Long,
    val stepStartedAt: Long,
    val actionAttempts: Map<String, Int>,
    val lastAnnouncedMessage: String?
)

internal object WeChatFailureDiagnostics {

    fun build(
        message: String,
        session: WeChatFailureSnapshot?,
        root: AccessibilityNodeInfo?,
        service: AccessibilityService
    ): String {
        return buildString {
            append("failure=").append(message)
            if (session != null) {
                append("\nstep=").append(session.step)
                append(", contact=").append(session.contactName)
                append(", startedAt=").append(session.startedAt)
                append(", stepStartedAt=").append(session.stepStartedAt)
                append(", now=").append(System.currentTimeMillis())
                append(", actionAttempts=").append(session.actionAttempts)
                append(", lastAnnouncedMessage=").append(session.lastAnnouncedMessage)
            }
            append("\nroot=").append(AccessibilityUtil.summarizeNode(root))
            append("\nwindows=").append(describeWindows(service))
            append("\nnodeTree=\n").append(AccessibilityUtil.dumpTree(root))
        }
    }

    fun describeWindows(service: AccessibilityService): String {
        val summaries = service.windows.orEmpty().mapIndexed { index, window ->
            val root = window.root
            val summary = buildString {
                append("#").append(index)
                append("(type=").append(window.type)
                append(", active=").append(window.isActive)
                append(", focused=").append(window.isFocused)
                append(", layer=").append(window.layer)
                append(", root=").append(AccessibilityUtil.summarizeNode(root))
                append(")")
            }
            AccessibilityUtil.safeRecycle(root)
            summary
        }
        return if (summaries.isEmpty()) "none" else summaries.joinToString("; ")
    }

    fun logDebugLong(tag: String, message: String) {
        message.chunked(3000).forEachIndexed { index, chunk ->
            DebugLog.d(tag) { "[$index] $chunk" }
        }
    }

    fun logErrorLong(tag: String, message: String) {
        message.chunked(3000).forEachIndexed { index, chunk ->
            DebugLog.e(tag, "[$index] $chunk")
        }
    }
}
