package com.yinxing.launcher.feature.settings

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class SettingsScreenController(private val activity: SettingsActivity) {
    fun bindStandard() = with(activity) {
        configureNavigation(
            R.id.btn_detail_contacts,
            R.string.settings_contacts_title,
            R.string.settings_contacts_summary,
            R.drawable.ic_settings_category_contacts,
            R.color.launcher_contacts,
            R.color.launcher_contacts_soft
        )
        configureNavigation(
            R.id.btn_detail_calls,
            R.string.settings_calls_title,
            R.string.settings_calls_summary,
            R.drawable.ic_settings_category_calls,
            R.color.launcher_call,
            R.color.launcher_call_soft
        )
        configureNavigation(
            R.id.btn_detail_permissions,
            R.string.settings_permissions_title,
            R.string.settings_permissions_summary,
            R.drawable.ic_settings_category_permissions,
            R.color.launcher_warning,
            R.color.launcher_warning_soft
        )
        configureNavigation(
            R.id.btn_detail_device,
            R.string.settings_device_title,
            R.string.settings_device_summary,
            R.drawable.ic_settings_category_device,
            R.color.launcher_contacts,
            R.color.launcher_contacts_soft
        )
        configureNavigation(
            R.id.btn_detail_system,
            R.string.settings_section_system_title,
            R.string.settings_system_summary_short,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft
        )

        findViewById<View>(R.id.btn_switch_mode).setOnClickListener {
            showScreen(SettingsScreen.ElderOverview)
        }
        findViewById<View>(R.id.btn_check_update).setOnClickListener { checkAppUpdate() }
    }

    fun bindElder() = with(activity) {
        findViewById<View>(R.id.btn_elder_back).setOnClickListener {
            showScreen(SettingsScreen.StandardOverview)
        }
        findViewById<View>(R.id.btn_switch_standard).setOnClickListener {
            showScreen(SettingsScreen.StandardOverview)
        }
        findViewById<View>(R.id.btn_elder_guard).setOnClickListener {
            dialogController.showIncomingGuardDialog()
        }
        findViewById<View>(R.id.btn_elder_contacts).setOnClickListener {
            showElderContactsDialog()
        }
        findViewById<View>(R.id.btn_elder_device).setOnClickListener {
            if (actionController.isDefaultLauncher()) {
                showPermissionGroupDialog(PermissionGroup.KeepAlive)
            } else {
                actionController.showSetDefaultLauncherDialog()
            }
        }
        findViewById<View>(R.id.btn_elder_system).setOnClickListener {
            dialogController.showSystemDialog()
        }
        bindElderAutoAnswer()
    }

    private fun SettingsActivity.showElderContactsDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_settings_contacts, null, false)
        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        content.findViewById<View>(R.id.btn_dialog_phone_contacts).setOnClickListener {
            dialog.dismiss()
            startActivity(PhoneContactActivity.createIntent(this, true))
        }
        content.findViewById<View>(R.id.btn_dialog_video_contacts).setOnClickListener {
            dialog.dismiss()
            startActivity(VideoCallActivity.createIntent(this, true))
        }
        content.findViewById<View>(R.id.btn_dialog_contacts_cancel).setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.76f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    fun refreshActive() {
        if (activity.currentScreen == SettingsScreen.ElderOverview) refreshElder()
    }

    private fun SettingsActivity.configureNavigation(
        rootId: Int,
        titleRes: Int,
        summaryRes: Int,
        iconRes: Int,
        tintRes: Int,
        plateRes: Int
    ) {
        val root = findViewById<View>(rootId)
        root.findViewById<TextView>(R.id.navigation_title).setText(titleRes)
        root.findViewById<TextView>(R.id.navigation_summary).setText(summaryRes)
        root.findViewById<ImageView>(R.id.navigation_icon).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@configureNavigation, tintRes))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@configureNavigation, plateRes))
        }
    }

    private fun SettingsActivity.bindElderAutoAnswer() {
        findViewById<SettingsToggle>(R.id.switch_elder_auto_answer)
            .setOnCheckedChangeListener { _, checked ->
                launcherPreferences.setAutoAnswerEnabled(checked)
                overviewController.refreshOverviewUi()
            }
    }

    private fun refreshElder() = with(activity) {
        val readiness = incomingGuardReadiness
        val total = readiness.items.size.coerceAtLeast(6)
        val pending = (total - readiness.completedCount).coerceAtLeast(0)
        findViewById<SettingsProgressView>(R.id.tv_elder_progress_count).apply {
            text = "${readiness.completedCount} / $total"
            setProgress(readiness.completedCount, total)
        }
        findViewById<TextView>(R.id.tv_elder_pending_count).text =
            getString(R.string.settings_elder_pending_count, pending)
        findViewById<TextView>(R.id.tv_elder_guard_summary).text = if (readiness.isReady) {
            getString(R.string.settings_incoming_guard_summary_ready)
        } else {
            readiness.blocker?.item?.let(overviewController::guardTitle)?.let {
                getString(R.string.settings_incoming_guard_pending_item, it)
            }.orEmpty()
        }
        findViewById<TextView>(R.id.tv_elder_city).text = weatherPreferences.getCityName()
        findViewById<TextView>(R.id.tv_elder_device_status).setText(
            if (actionController.isDefaultLauncher()) R.string.settings_guard_status_done
            else R.string.settings_guard_status_pending
        )

        val switch = findViewById<SettingsToggle>(R.id.switch_elder_auto_answer)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = launcherPreferences.isAutoAnswerEnabled()
        findViewById<TextView>(R.id.tv_elder_auto_summary).text = if (switch.isChecked) {
            getString(R.string.settings_auto_answer_delay_summary, launcherPreferences.getAutoAnswerDelaySeconds())
        } else {
            getString(R.string.settings_auto_answer_summary_off)
        }
        bindElderAutoAnswer()

        lifecycleScope.launch {
            try {
                val counts = overviewController.loadContactCounts()
                if (currentScreen == SettingsScreen.ElderOverview) {
                    findViewById<TextView>(R.id.tv_elder_contacts_count).text =
                        getString(R.string.settings_elder_contacts_count, counts.phoneCount + counts.videoCount)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (currentScreen == SettingsScreen.ElderOverview) {
                    findViewById<TextView>(R.id.tv_elder_contacts_count).text =
                        getString(R.string.settings_elder_contacts_count, 0)
                }
            }
        }
    }
}
