package com.yinxing.launcher.feature.callreturn

import android.content.Context
import android.content.Intent
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.feature.home.MainActivity

internal object CallReturnHomeIntentFactory {
    fun create(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
    }
}

internal object CallReturnHomeLauncher {
    private const val TAG = "CallReturnHome"

    fun launch(context: Context): Boolean = runCatching {
        context.startActivity(CallReturnHomeIntentFactory.create(context))
        DebugLog.i(TAG) { "[通话返回] 已返回银杏首页" }
        true
    }.getOrElse { error ->
        DebugLog.w(TAG, "[通话返回] 返回首页失败: ${error.message}")
        false
    }
}
