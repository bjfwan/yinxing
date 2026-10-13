package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil

/**
 * 识别并处理系统运行时权限弹窗（permissioncontroller / packageinstaller）。
 *
 * 微信在发起视频通话前可能先展示自己的权限引导页（"在下方弹窗中选择允许"），
 * 真正的授权弹窗属于系统包，不在微信窗口树内——因此需要单独读取 active window。
 */
internal object WeChatPermissionDialogHandler {

    private val PERMISSION_PACKAGES = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.vivo.permissionmanager"
    )

    private val ALLOW_TEXTS = listOf(
        "仅在使用该应用时允许",
        "仅在使用中允许",
        "使用期间允许",
        "始终允许",
        "仅本次允许",
        "允许"
    )

    fun matchesPackage(packageName: String?): Boolean {
        return packageName in PERMISSION_PACKAGES
    }

    fun isPermissionPrompt(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (!matchesPackage(root.packageName?.toString())) return false
        val allowNode = findAllowNode(root)
        AccessibilityUtil.safeRecycle(allowNode)
        return allowNode != null
    }

    fun clickAllow(service: AccessibilityService, root: AccessibilityNodeInfo?): Boolean {
        val node = findAllowNode(root) ?: return false
        return try {
            AccessibilityUtil.performClick(service, node)
        } finally {
            AccessibilityUtil.safeRecycle(node)
        }
    }

    private fun findAllowNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        for (text in ALLOW_TEXTS) {
            val node = AccessibilityUtil.findBestTextNode(
                root,
                text,
                exactMatch = true,
                preferBottom = false
            )
            if (node != null) return node
        }
        return null
    }
}
