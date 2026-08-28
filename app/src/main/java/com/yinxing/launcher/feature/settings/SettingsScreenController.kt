package com.yinxing.launcher.feature.settings

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterSetting
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class SettingsScreenController(private val activity: SettingsActivity) {
    fun bindStandard() = with(activity) {
        configureNavigation(
            R.id.btn_family_setup,
            R.string.family_setup_reopen_title,
            R.string.family_setup_reopen_summary,
            R.drawable.ic_settings_category_contacts,
            R.color.launcher_ginkgo_deep,
            R.color.launcher_ginkgo_soft
        )
        findViewById<View>(R.id.btn_family_setup)
            .findViewById<TextView>(R.id.navigation_value)
            .setText(R.string.family_setup_reopen_value)
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
            R.id.btn_detail_diagnostics,
            R.string.settings_diagnostics_title,
            R.string.settings_diagnostics_summary,
            R.drawable.ic_settings_action_incoming_guard,
            R.color.launcher_system,
            R.color.launcher_system_soft
        )
        configureNavigation(
            R.id.btn_detail_safety,
            R.string.settings_safety_title,
            R.string.settings_safety_summary,
            R.drawable.ic_settings_action_warning,
            R.color.launcher_danger,
            R.color.launcher_danger_soft
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
            R.id.btn_detail_display,
            R.string.settings_display_title,
            R.string.settings_display_summary,
            R.drawable.ic_settings_device_display,
            R.color.launcher_system,
            R.color.launcher_system_soft
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
            R.id.btn_detail_background,
            R.string.settings_background_title,
            R.string.settings_background_summary,
            R.drawable.ic_settings_permission_background,
            R.color.launcher_ginkgo_deep,
            R.color.launcher_ginkgo_soft
        )
        configureNavigation(
            R.id.btn_detail_weather,
            R.string.settings_weather_title,
            R.string.settings_weather_summary_short,
            R.drawable.ic_weather_sun,
            R.color.launcher_device,
            R.color.launcher_device_soft
        )
        configureNavigation(
            R.id.btn_detail_system,
            R.string.settings_section_system_title,
            R.string.settings_system_summary_short,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft
        )
        configureNavigation(
            R.id.btn_detail_about,
            R.string.settings_about_title,
            R.string.settings_about_summary,
            R.drawable.ic_settings_permission_accessibility,
            R.color.launcher_ginkgo_deep,
            R.color.launcher_ginkgo_soft
        )

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
        findViewById<View>(R.id.btn_elder_about).setOnClickListener {
            showScreen(SettingsScreen.About)
        }
        bindElderAutoAnswer()
        bindElderFallDetection()
    }

    private fun SettingsActivity.showElderContactsDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_settings_contacts, null, false)
        val dialog = LauncherDialogFactory.create(this, content)
        content.findViewById<View>(R.id.btn_dialog_phone_contacts).setOnClickListener {
            dialog.dismiss()
            startActivity(PhoneContactActivity.createIntent(this, true))
        }
        content.findViewById<View>(R.id.btn_dialog_video_contacts).setOnClickListener {
            dialog.dismiss()
            startActivity(VideoCallActivity.createIntent(this, true))
        }
        content.findViewById<View>(R.id.btn_dialog_contacts_cancel).setOnClickListener { dialog.dismiss() }
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
                LobsterClient.reportUsage(
                    this,
                    LobsterSettingEventFactory.toggleChanged(LobsterSetting.AUTO_ANSWER, checked)
                )
                overviewController.refreshOverviewUi()
            }
    }

    private fun SettingsActivity.bindElderFallDetection() {
        findViewById<SettingsToggle>(R.id.switch_elder_fall_detection)
            .setOnCheckedChangeListener { _, checked ->
                detailController.onFallDetectionToggle(checked)
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

        val fallSwitch = findViewById<SettingsToggle>(R.id.switch_elder_fall_detection)
        fallSwitch.setOnCheckedChangeListener(null)
        fallSwitch.isChecked = launcherPreferences.isFallDetectionEnabled()
        findViewById<TextView>(R.id.tv_elder_fall_summary).setText(
            if (fallSwitch.isChecked) R.string.settings_elder_fall_summary_on
            else R.string.settings_elder_fall_summary_off
        )
        bindElderFallDetection()

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
