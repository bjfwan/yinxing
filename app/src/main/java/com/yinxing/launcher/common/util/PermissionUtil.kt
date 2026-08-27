package com.yinxing.launcher.common.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionUtil {

    private const val TAG = "PermissionUtil"
    private const val INCOMING_CALL_CHANNEL_ID = "incoming_call_alerts"

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

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /** 检查当前应用是否为默认桌面 */
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            return roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val packageName = info?.activityInfo?.packageName
        // 如果没有设置默认桌面，系统可能会返回 "android" (ResolverActivity) 或 null
        return packageName == context.packageName
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!areIncomingNotificationsEnabled(context)) return false
        return canUseIncomingCallFullScreenIntent(context)
    }

    fun openNotificationSettings(context: Context) {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                areIncomingNotificationsEnabled(context) &&
                !canUseIncomingCallFullScreenIntent(context)
            ) {
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}")
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }.onFailure {
            openAppDetailSettings(context)
        }
    }

    private fun areIncomingNotificationsEnabled(context: Context): Boolean {
        if (!runCatching {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }.getOrDefault(false)
        ) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(INCOMING_CALL_CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun canUseIncomingCallFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return runCatching {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        }.getOrDefault(false)
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
            runCatching {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(fallback)
            }.onFailure { e ->
                DebugLog.w(TAG, "无法打开电池优化设置: ${e.message}")
            }
        }
    }

    fun hasManageExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
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

    fun hasPhonePermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) ==
                PackageManager.PERMISSION_GRANTED)
    }


    fun canStartBackgroundActivity(): Boolean = false

    fun openBackgroundStartSettings(context: Context) {
        openOemSettings(context, OemSettingsCapability.BACKGROUND_START)
    }

    fun isAutoStartEnabled(): Boolean = false

    fun openAutoStartSettings(context: Context) {
        openOemSettings(context, OemSettingsCapability.AUTO_START)
    }

    private fun openOemSettings(context: Context, capability: OemSettingsCapability) {
        val targets = OemSettingsCatalog.targets(Build.MANUFACTURER, capability)
        for (target in targets) {
            val intent = Intent().apply {
                target.action?.let(::setAction)
                target.packageName?.let(::setPackage)
                if (target.packageName != null && target.className != null) {
                    component = ComponentName(target.packageName, target.className)
                }
                target.packageExtraKey?.let { putExtra(it, context.packageName) }
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        openAppDetailSettings(context)
    }

    fun openAppDetailSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }.onFailure { e ->
            DebugLog.w(TAG, "无法打开应用详情页: ${e.message}")
        }
    }
}
