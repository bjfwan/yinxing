package com.yinxing.launcher.feature.home

import android.content.Intent
import android.os.SystemClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterUsageEvents
import com.yinxing.launcher.common.lobster.LobsterTrace
import com.yinxing.launcher.common.lobster.withTrace
import com.yinxing.launcher.common.perf.LauncherTraceNames
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.settings.SettingsActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import com.yinxing.launcher.feature.weather.WeatherDetailActivity

class HomeNavigator(
    private val activity: AppCompatActivity
) {
    fun openWeatherEntry() {
        LobsterClient.reportUsage(
            activity,
            LobsterUsageEvents.HOME_WEATHER_OPENED.withTrace(LobsterTrace.newId())
        )
        activity.startActivity(Intent(activity, WeatherDetailActivity::class.java))
    }

    fun openAppManager() {
        LobsterClient.reportUsage(
            activity,
            LobsterUsageEvents.HOME_APP_MANAGER_OPENED.withTrace(LobsterTrace.newId())
        )
        activity.startActivity(Intent(activity, AppManageActivity::class.java))
    }

    fun showCaregiverEntryDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text =
            activity.getString(R.string.home_caregiver_dialog_title)
        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text =
            activity.getString(R.string.home_caregiver_dialog_message)
        dialogView.findViewById<TextView>(R.id.tv_cancel_label).text =
            activity.getString(R.string.home_caregiver_dialog_cancel)
        dialogView.findViewById<TextView>(R.id.tv_primary_label).text =
            activity.getString(R.string.home_caregiver_dialog_confirm)
        val dialog = LauncherDialogFactory.create(activity, dialogView)
        dialogView.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btn_open_settings).setOnClickListener {
            dialog.dismiss()
            LobsterClient.reportUsage(
                activity,
                LobsterUsageEvents.CAREGIVER_SETTINGS_OPENED.withTrace(LobsterTrace.newId())
            )
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        dialog.show()
    }

    fun openHomeItem(item: HomeAppItem) {
        when (item.type) {
            HomeAppItem.Type.APP -> openApp(item)
            HomeAppItem.Type.PHONE -> {
                LobsterClient.reportUsage(
                    activity,
                    LobsterUsageEvents.HOME_PHONE_OPENED.withTrace(LobsterTrace.newId())
                )
                activity.startActivity(Intent(activity, PhoneContactActivity::class.java))
            }
            HomeAppItem.Type.WECHAT_VIDEO -> {
                LobsterClient.log("[首页] 点击微信视频卡片")
                activity.startActivity(
                    Intent(activity, VideoCallActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            HomeAppItem.Type.ADD -> openAppManager()
        }
    }

    private fun openApp(item: HomeAppItem) {
        val traceId = LobsterTrace.newId()
        val startedAt = SystemClock.elapsedRealtime()
        val intent = activity.packageManager.getLaunchIntentForPackage(item.packageName)
        if (intent != null) {
            runCatching { activity.startActivity(intent) }
                .onSuccess {
                    LobsterClient.reportUsage(activity, LobsterUsageEvents.APP_OPENED.withTrace(traceId))
                }
                .onFailure {
                    LobsterClient.reportUsage(activity, LobsterUsageEvents.APP_OPEN_FAILED.withTrace(traceId))
                    showOpenAppFailed(item)
                }
        } else {
            LobsterClient.reportUsage(activity, LobsterUsageEvents.APP_OPEN_FAILED.withTrace(traceId))
            showOpenAppFailed(item)
        }
        LobsterClient.reportMetrics(
            activity,
            listOf(LauncherTraceNames.HOME_APP_LAUNCH to (SystemClock.elapsedRealtime() - startedAt)),
            traceId
        )
    }

    private fun showOpenAppFailed(item: HomeAppItem) {
        Toast.makeText(
            activity,
            activity.getString(R.string.open_app_failed, item.appName),
            Toast.LENGTH_SHORT
        ).show()
    }
}
