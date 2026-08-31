package com.yinxing.launcher.feature.callreturn

import android.content.Context
import android.os.SystemClock
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.data.home.LauncherPreferences

internal object CallReturnCoordinator {
    private const val TAG = "CallReturnCoordinator"
    private val controller = CallReturnSessionController()

    fun arm(
        context: Context,
        origin: CallReturnOrigin,
        requestId: String,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val enabled = LauncherPreferences.getInstance(context).isReturnHomeAfterCallEnabled()
        val armed = controller.arm(enabled, origin, requestId, nowMs)
        DebugLog.i(TAG) { "[通话返回] arm origin=$origin enabled=$enabled armed=$armed" }
        return armed
    }

    fun confirm(
        origin: CallReturnOrigin,
        requestId: String? = null,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val confirmed = controller.confirm(origin, requestId, nowMs)
        if (confirmed) DebugLog.i(TAG) { "[通话返回] confirmed origin=$origin" }
        return confirmed
    }

    fun cancel(origin: CallReturnOrigin, requestId: String? = null) {
        controller.cancel(origin, requestId)
        DebugLog.i(TAG) { "[通话返回] cancelled origin=$origin" }
    }

    fun observeWindow(
        context: Context,
        packageName: String?,
        className: String?,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): CallReturnWindowAction {
        val session = controller.snapshot()?.takeIf { it.confirmed }
            ?: return CallReturnWindowAction.IGNORE
        val action = CallReturnWindowPolicy.decide(
            origin = session.origin,
            appPackage = context.packageName,
            packageName = packageName,
            className = className
        )
        if (action == CallReturnWindowAction.USER_ESCAPED) {
            controller.markUserEscaped(nowMs)
            DebugLog.i(TAG) { "[通话返回] 用户已离开通话页面 pkg=$packageName" }
        }
        return action
    }

    fun complete(
        context: Context,
        origin: CallReturnOrigin,
        requestId: String? = null,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (!LauncherPreferences.getInstance(context).isReturnHomeAfterCallEnabled()) {
            controller.cancel(origin, requestId)
            return false
        }
        return if (controller.complete(origin, requestId, nowMs) == CallReturnAction.RETURN_HOME) {
            CallReturnHomeLauncher.launch(context)
        } else {
            false
        }
    }

    fun hasConfirmedSession(origin: CallReturnOrigin): Boolean =
        controller.snapshot()?.let { it.origin == origin && it.confirmed && !it.userEscaped } == true

    fun hasSession(origin: CallReturnOrigin): Boolean =
        controller.snapshot()?.origin == origin
}
