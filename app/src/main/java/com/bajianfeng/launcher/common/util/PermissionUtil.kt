package com.bajianfeng.launcher.common.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object PermissionUtil {

    /** 壳服务完整类名，微信会检测该服务是否运行以决定是否开放节点树 */
    const val SELECT_TO_SPEAK_SERVICE =
        "com.bajianfeng.launcher/com.google.android.accessibility.selecttospeak.SelectToSpeakService"

    fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean {
        return isAccessibilityServiceEnabled(
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ),
            serviceName
        )
    }

    fun isAccessibilityServiceEnabled(enabledServices: String?, serviceName: String): Boolean {
        return AccessibilityServiceMatcher.contains(enabledServices, serviceName)
    }

    /**
     * 检查微信视频通话所需的两个无障碍服务是否都已开启：
     * 1. 主服务（WeChatAccessibilityService）
     * 2. 壳服务（SelectToSpeakService）——触发微信白名单开放节点树
     */
    fun isWeChatCallAccessibilityReady(context: Context, mainServiceName: String): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return isAccessibilityServiceEnabled(enabled, mainServiceName) &&
            isAccessibilityServiceEnabled(enabled, SELECT_TO_SPEAK_SERVICE)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
