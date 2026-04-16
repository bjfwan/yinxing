package com.bajianfeng.launcher.common.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.bajianfeng.launcher.feature.incoming.WeChatIncomingCallService

object PermissionUtil {

    private const val TAG = "PermissionUtil"

    /** 壳服务完整类名，微信会检测该服务是否运行以决定是否开放节点树 */
    const val SELECT_TO_SPEAK_SERVICE =
        "com.bajianfeng.launcher/com.google.android.accessibility.selecttospeak.SelectToSpeakService"

    // ── 无障碍服务 ────────────────────────────────────────────────────────────

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

    fun isAnyAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return !enabled.isNullOrBlank()
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

    // ── 悬浮窗 ────────────────────────────────────────────────────────────────

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    // ── 通知监听 ──────────────────────────────────────────────────────────────

    /**
     * 检查通知监听权限（WeChatIncomingCallService 所需）。
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val componentName = ComponentName(context, WeChatIncomingCallService::class.java)
        return flat.split(":").any { entry ->
            runCatching { ComponentName.unflattenFromString(entry) == componentName }.getOrDefault(false)
        }
    }

    /** 跳转到系统通知使用权设置页 */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    // ── 电池优化豁免 ──────────────────────────────────────────────────────────

    /** 是否已被加入电池优化白名单（豁免） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 跳转到"请求忽略电池优化"对话框。
     * 需要在 Manifest 中声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。
     */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }.onFailure {
            // 部分设备不支持直接请求，跳转到全部应用的电池优化列表
            runCatching {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(fallback)
            }.onFailure { e ->
                Log.w(TAG, "无法打开电池优化设置: ${e.message}")
            }
        }
    }

    // ── 管理外部存储（Android 11+）────────────────────────────────────────────

    fun hasManageExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 及以下无需此权限
        }
    }

    fun openManageExternalStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallback)
                }
            }
        }
    }

    // ── 自启动（厂商专属，无标准 API）────────────────────────────────────────

    /**
     * 自启动无标准 Android API，保守返回 false（不可判断）。
     * UI 层应始终显示"去设置"入口，让用户手动确认。
     */
    fun isAutoStartEnabled(): Boolean = false

    /**
     * 跳转到厂商自启动管理页。
     * 覆盖：MIUI、EMUI/HarmonyOS、ColorOS（OPPO/Realme）、OriginOS/FuntouchOS（VIVO）、Samsung
     * 如果找不到则跳转到本应用的系统详情页。
     */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            // MIUI（小米/红米）
            Intent().setComponent(ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // EMUI / HarmonyOS（华为/荣耀）
            Intent().setComponent(ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")),
            // ColorOS（OPPO/Realme）
            Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.privacypermissionsentry.PermissionTopActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity")),
            // OriginOS / FuntouchOS（VIVO）
            Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Samsung
            Intent().setComponent(ComponentName("com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"))
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching {
                context.startActivity(intent)
                return
            }
        }
        // 所有厂商页均不可用，退回到系统应用详情页
        openAppDetailSettings(context)
    }

    /** 跳转到本应用系统详情页 */
    fun openAppDetailSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }.onFailure { e ->
            Log.w(TAG, "无法打开应用详情页: ${e.message}")
        }
    }
}
