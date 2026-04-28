package com.yinxing.launcher.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.settings.SettingsActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity

class HomeNavigator(
    private val activity: AppCompatActivity
) {
    fun openWeatherEntry() {
        val vendorIntent = listOf(
            "com.miui.weather2",
            "com.huawei.android.totemweather",
            "com.oppo.weather",
            "com.vivo.weather"
        ).asSequence().mapNotNull { activity.packageManager.getLaunchIntentForPackage(it) }.firstOrNull()
        if (vendorIntent != null) {
            activity.startActivity(vendorIntent)
            return
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.weather_fallback_url)))
        runCatching { activity.startActivity(browserIntent) }
            .onSuccess {
                Toast.makeText(activity, activity.getString(R.string.weather_fallback_notice), Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(activity, activity.getString(R.string.weather_not_available), Toast.LENGTH_SHORT).show()
            }
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
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btn_open_settings).setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        dialog.show()
    }

    fun openHomeItem(item: HomeAppItem) {
        when (item.type) {
            HomeAppItem.Type.APP -> openApp(item)
            HomeAppItem.Type.PHONE -> activity.startActivity(Intent(activity, PhoneContactActivity::class.java))
            HomeAppItem.Type.WECHAT_VIDEO -> activity.startActivity(Intent(activity, VideoCallActivity::class.java))
            HomeAppItem.Type.ADD -> activity.startActivity(Intent(activity, AppManageActivity::class.java))
        }
    }

    private fun openApp(item: HomeAppItem) {
        val intent = activity.packageManager.getLaunchIntentForPackage(item.packageName)
        if (intent != null) {
            activity.startActivity(intent)
        } else {
            Toast.makeText(
                activity,
                activity.getString(R.string.open_app_failed, item.appName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
