package com.yinxing.launcher.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.accessibility.selecttospeak.WeChatDeviceTestScenarioStore

class WeChatDeviceTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CLEAR, false) == true) {
            WeChatDeviceTestScenarioStore.clear()
            resultCode = Activity.RESULT_OK
            resultData = "cleared"
            return
        }
        val armed = WeChatDeviceTestScenarioStore.arm(
            routeName = intent?.getStringExtra(EXTRA_ROUTE),
            failuresCsv = intent?.getStringExtra(EXTRA_FAILURES)
        )
        resultCode = if (armed) Activity.RESULT_OK else Activity.RESULT_CANCELED
        resultData = if (armed) "armed" else "invalid"
    }

    companion object {
        const val ACTION = "com.yinxing.launcher.DEBUG_WECHAT_SCENARIO"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_FAILURES = "failures"
        const val EXTRA_CLEAR = "clear"
    }
}
