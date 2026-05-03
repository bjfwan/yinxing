package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.data.home.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kiosk 模式下"桌面拉回"逻辑。
 *
 * 之所以从 [SelectToSpeakService] 抽离，是因为它跟微信视频自动化逻辑完全正交：
 * - 自动化只关心微信窗口；
 * - 这里只关心 "Kiosk 开启时，是否要把 [launcherActivityClass] 强制带回前台"。
 *
 * 调用方需要在以下时机通知本 Guard：
 * - 服务连接完成后调 [init]；
 * - 收到 [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] 时调 [onWindowStateChanged]，
 *   返回值表示"是否吞掉本事件"——若 true，自动化主流程应直接 return 不再处理此事件。
 * - 服务销毁时调 [shutdown]。
 */
internal class KioskLauncherGuard(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val launcherActivityClass: Class<*>,
    private val activeSession: () -> Boolean
) {
    private companion object {
        private const val TAG = "KioskLauncherGuard"
        private const val LAUNCHER_STATE_SETTLE_DELAY_MS = 450L
        private const val HIBOARD_OVERVIEW_SUPPRESS_WINDOW_MS = 1500L
        private const val LAUNCHER_RETRY_DELAY_MS = 350L
        private const val PKG_BBK_LAUNCHER = "com.bbk.launcher2"
        private const val PKG_VIVO_HIBOARD = "com.vivo.hiboard"
        private const val OVERVIEW_PANEL_ID = "com.vivo.recents:id/overview_panel2"
        private const val OVERVIEW_CLEAR_ALL_TEXT = "清除全部"
    }

    private var systemLauncherPackages: Set<String> = emptySet()
    private var defaultLauncherPackage: String? = null
    private var lastLauncherOverviewAt = 0L
    private var bringBackJob: Job? = null

    fun init() {
        systemLauncherPackages = resolveSystemLauncherPackages()
        defaultLauncherPackage = resolveDefaultLauncherPackage()
    }

    /**
     * @return true 表示当前事件是"桌面前台"事件，已被本 Guard 处理，调用方不应再处理。
     */
    fun onWindowStateChanged(pkg: String, className: String?): Boolean {
        if (shouldObserveLauncherForeground(pkg)) {
            scheduleLauncherBringBackConfirmation(triggerPkg = pkg, triggerClassName = className)
            return true
        }
        cancelPendingConfirm()
        return false
    }

    fun shutdown() {
        cancelPendingConfirm()
    }

    private fun cancelPendingConfirm() {
        bringBackJob?.cancel()
        bringBackJob = null
    }

    private fun shouldObserveLauncherForeground(pkg: String): Boolean {
        if (!LauncherPreferences.getInstance(service).isKioskModeEnabled()) return false
        if (activeSession()) return false
        val defaultHome = defaultLauncherPackage ?: resolveDefaultLauncherPackage().also {
            defaultLauncherPackage = it
        }
        return pkg == defaultHome ||
            (pkg == PKG_VIVO_HIBOARD && defaultHome == PKG_BBK_LAUNCHER)
    }

    private fun scheduleLauncherBringBackConfirmation(triggerPkg: String, triggerClassName: String?) {
        cancelPendingConfirm()
        bringBackJob = scope.launch {
            delay(LAUNCHER_STATE_SETTLE_DELAY_MS)
            if (!LauncherPreferences.getInstance(service).isKioskModeEnabled()) return@launch
            if (activeSession()) return@launch

            val activeRoot = service.rootInActiveWindow
            val activePkg = activeRoot?.packageName?.toString() ?: triggerPkg
            val activeClassName = activeRoot?.className?.toString()
            AccessibilityUtil.safeRecycle(activeRoot)

            val effectiveClassName = if (activePkg == triggerPkg) {
                triggerClassName ?: activeClassName
            } else {
                activeClassName
            }
            if (shouldBringLauncherBack(activePkg, effectiveClassName)) {
                bringLauncherToFront()
            }
        }
    }

    private fun shouldBringLauncherBack(pkg: String, className: String?): Boolean {
        if (!LauncherPreferences.getInstance(service).isKioskModeEnabled()) return false
        if (activeSession()) return false

        val defaultHome = defaultLauncherPackage ?: resolveDefaultLauncherPackage().also {
            defaultLauncherPackage = it
        }
        val isLauncherOverview = isLauncherOverviewState(pkg, className)
        val now = System.currentTimeMillis()
        if (isLauncherOverview) {
            lastLauncherOverviewAt = now
        }
        val suppressHiboardAfterOverview =
            pkg == PKG_VIVO_HIBOARD &&
                defaultHome == PKG_BBK_LAUNCHER &&
                now - lastLauncherOverviewAt <= HIBOARD_OVERVIEW_SUPPRESS_WINDOW_MS
        val result = when {
            pkg == service.packageName -> false
            pkg == defaultHome && !isLauncherOverview -> true
            suppressHiboardAfterOverview -> false
            pkg == PKG_VIVO_HIBOARD && defaultHome == PKG_BBK_LAUNCHER -> true
            else -> false
        }
        DebugLog.d(TAG) {
            "shouldBringLauncherBack pkg=$pkg className=$className result=$result " +
                "defaultHome=$defaultHome overview=$isLauncherOverview " +
                "suppressHiboardAfterOverview=$suppressHiboardAfterOverview " +
                "sinceOverview=${now - lastLauncherOverviewAt} launchers=$systemLauncherPackages"
        }
        return result
    }

    private fun resolveSystemLauncherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return service.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toMutableSet()
            .apply { remove(service.packageName) }
    }

    private fun resolveDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return service.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeUnless { it == service.packageName }
    }

    private fun isLauncherOverviewState(pkg: String, className: String?): Boolean {
        if (pkg != PKG_BBK_LAUNCHER) return false
        if (className?.contains("Recents", ignoreCase = true) == true) return true
        if (className?.contains("Overview", ignoreCase = true) == true) return true
        return isLauncherOverviewActive(pkg)
    }

    private fun isLauncherOverviewActive(pkg: String): Boolean {
        if (pkg != PKG_BBK_LAUNCHER) return false
        val root = service.rootInActiveWindow ?: return false
        return try {
            val overviewNodes = AccessibilityUtil.findAllById(root, OVERVIEW_PANEL_ID)
            val clearAllNodes = AccessibilityUtil.findAllByText(root, OVERVIEW_CLEAR_ALL_TEXT)
            val result = overviewNodes.isNotEmpty() || clearAllNodes.isNotEmpty()
            overviewNodes.forEach { AccessibilityUtil.safeRecycle(it) }
            clearAllNodes.forEach { AccessibilityUtil.safeRecycle(it) }
            result
        } finally {
            AccessibilityUtil.safeRecycle(root)
        }
    }

    private fun bringLauncherToFront() {
        val intent = Intent(service, launcherActivityClass).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        }

        val startSent = tryStartLauncherActivity(intent, source = "directStart")

        val pendingIntentSent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            trySendLauncherPendingIntent(intent)
        } else {
            false
        }

        if (startSent || pendingIntentSent) {
            scope.launch {
                delay(LAUNCHER_RETRY_DELAY_MS)
                val activePackage = service.rootInActiveWindow?.packageName?.toString()
                if (activePackage != null && activePackage in systemLauncherPackages) {
                    DebugLog.d(TAG) { "bringLauncherToFront: retry after settle, activePackage=$activePackage" }
                    tryStartLauncherActivity(intent, source = "retryStart")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        trySendLauncherPendingIntent(intent, source = "retryPendingIntent")
                    }
                }
            }
            return
        }

        val homeOk = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        DebugLog.d(TAG) { "bringLauncherToFront: fallback globalHome=$homeOk" }
    }

    private fun tryStartLauncherActivity(intent: Intent, source: String): Boolean {
        return try {
            service.startActivity(intent)
            DebugLog.d(TAG) { "bringLauncherToFront: startActivity sent source=$source" }
            true
        } catch (e: Exception) {
            DebugLog.w(TAG, "bringLauncherToFront: startActivity failed source=$source error=${e.message}")
            false
        }
    }

    private fun trySendLauncherPendingIntent(intent: Intent, source: String = "pendingIntent"): Boolean {
        return try {
            val creatorOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                createPendingIntentCreatorOptions()
            } else {
                null
            }
            val pendingIntent = PendingIntent.getActivity(
                service,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions
            )
            val sendOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                createPendingIntentSendOptions()
            } else {
                null
            }
            pendingIntent.send(service, 0, null, null, null, null, sendOptions)
            DebugLog.d(TAG) { "bringLauncherToFront: PendingIntent sent source=$source" }
            true
        } catch (e: Exception) {
            DebugLog.w(TAG, "bringLauncherToFront: PendingIntent failed source=$source error=${e.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun createPendingIntentCreatorOptions(): Bundle {
        return ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun createPendingIntentSendOptions(): Bundle {
        return ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    }
}
