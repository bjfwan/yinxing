package com.yinxing.launcher.feature.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.OemLauncherIconLoader
import com.yinxing.launcher.common.util.OemLauncherPolicy
import com.yinxing.launcher.common.util.OemLauncherSupport
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.incoming.DefaultPhoneRoleController
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics
import com.yinxing.launcher.feature.incoming.OemIncomingCallPolicy
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity

internal class SettingsDetailController(private val activity: SettingsActivity) {
    fun bind(screen: SettingsScreen) {
        if (screen in setOf(SettingsScreen.StandardOverview, SettingsScreen.ElderOverview)) return
        activity.findViewById<View>(R.id.btn_detail_back).setOnClickListener {
            activity.showScreen(SettingsScreen.StandardOverview)
        }
        activity.findViewById<TextView>(R.id.settings_detail_page_title).setText(screen.titleRes)
        activity.findViewById<TextView>(R.id.settings_detail_page_subtitle).setText(screen.subtitleRes())
        activity.findViewById<LinearLayout>(R.id.settings_detail_rows).removeAllViews()

        when (screen) {
            SettingsScreen.Contacts -> bindContacts()
            SettingsScreen.Calls -> bindCalls()
            SettingsScreen.Permissions -> bindPermissions()
            SettingsScreen.Device -> bindDevice()
            SettingsScreen.System -> bindSystem()
            else -> Unit
        }
    }

    private fun bindContacts() = with(activity) {
        addRow(
            R.string.settings_manage_phone_contacts_title,
            R.string.settings_manage_phone_contacts_summary,
            R.drawable.ic_settings_category_contacts,
            R.color.launcher_contacts,
            R.color.launcher_contacts_soft,
            chevron = true
        ) {
            startActivity(PhoneContactActivity.createIntent(this, startInManageMode = true))
        }
        addRow(
            R.string.settings_manage_video_contacts_title,
            R.string.settings_manage_video_contacts_summary,
            R.drawable.ic_settings_category_contacts,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            chevron = true
        ) {
            startActivity(VideoCallActivity.createIntent(this, startInManageMode = true))
        }
        addRow(
            R.string.settings_manage_home_apps_title,
            R.string.settings_manage_home_apps_summary,
            R.drawable.ic_settings_permission_overlay,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            chevron = true
        ) {
            startActivity(Intent(this, AppManageActivity::class.java))
        }
    }

    private fun bindCalls() = with(activity) {
        val isDefaultPhone = DefaultPhoneRoleController.isHeld(this)
        addRow(
            R.string.settings_default_phone_title,
            if (isDefaultPhone) {
                getString(R.string.settings_default_phone_summary_on)
            } else {
                getString(R.string.settings_default_phone_summary_off)
            },
            R.drawable.ic_settings_category_calls,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            action = getString(
                if (isDefaultPhone) R.string.settings_guard_status_done
                else R.string.settings_default_phone_action
            )
        ) {
            actionController.requestDefaultPhoneRole()
        }
        val vendorPolicy = OemIncomingCallPolicy.forManufacturer(Build.MANUFACTURER)
        addRow(
            R.string.settings_incoming_vendor_title,
            getString(R.string.settings_incoming_vendor_summary, vendorPolicy.vendorName),
            R.drawable.ic_settings_permission_background,
            R.color.launcher_warning,
            R.color.launcher_warning_soft,
            action = getString(R.string.settings_action_check),
            chevron = true
        ) {
            actionController.showIncomingCallVendorDialog(vendorPolicy)
        }
        addSwitchRow(
            R.string.settings_auto_answer_title,
            if (launcherPreferences.isAutoAnswerEnabled()) {
                R.string.settings_auto_answer_summary_on
            } else {
                R.string.settings_auto_answer_summary_off
            },
            R.drawable.ic_settings_category_calls,
            launcherPreferences.isAutoAnswerEnabled()
        ) {
            launcherPreferences.setAutoAnswerEnabled(it)
            bind(SettingsScreen.Calls)
            overviewController.refreshOverviewUi()
        }
        addRow(
            R.string.settings_auto_answer_delay_title,
            getString(R.string.settings_auto_answer_delay_summary, launcherPreferences.getAutoAnswerDelaySeconds()),
            R.drawable.ic_settings_category_calls,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            action = getString(R.string.settings_action_adjust)
        ) {
            showValueDialog(
                title = getString(R.string.settings_delay_dialog_title),
                initial = launcherPreferences.getAutoAnswerDelaySeconds(),
                range = 1..30,
                format = { "$it 秒" },
                onSave = launcherPreferences::setAutoAnswerDelaySeconds
            )
        }
        addSwitchRow(
            R.string.settings_full_card_tap_title,
            if (launcherPreferences.isFullCardTapEnabled()) {
                R.string.settings_full_card_tap_summary_on
            } else {
                R.string.settings_full_card_tap_summary_off
            },
            R.drawable.ic_settings_category_contacts,
            launcherPreferences.isFullCardTapEnabled()
        ) {
            launcherPreferences.setFullCardTapEnabled(it)
            bind(SettingsScreen.Calls)
        }
        addRow(
            R.string.settings_incoming_trace_title,
            IncomingCallDiagnostics.getDisplayText(this),
            R.drawable.ic_settings_action_incoming_guard,
            R.color.launcher_system,
            R.color.launcher_system_soft
        )
    }

    private fun bindPermissions() = with(activity) {
        PermissionGroup.entries.forEach { group ->
            val state = overviewController.permissionGroupRenderState(group)
            addRow(
                group.titleRes,
                state.summary,
                R.drawable.ic_settings_category_permissions,
                state.badge.textColorResId,
                state.badge.backgroundColorResId,
                action = state.badge.text,
                actionColorRes = state.badge.textColorResId,
                chevron = true
            ) {
                showPermissionGroupDialog(group)
            }
        }
    }

    private fun bindDevice() = with(activity) {
        val isDefaultLauncher = actionController.isDefaultLauncher()
        val launcherProfile = OemLauncherPolicy.profile(Build.MANUFACTURER)
        val defaultLauncherRow = addRow(
            R.string.settings_default_launcher_title,
            if (isDefaultLauncher) {
                getString(R.string.set_default_launcher_summary_on)
            } else {
                getString(
                    when (launcherProfile.support) {
                        OemLauncherSupport.RESTRICTED -> R.string.set_default_launcher_summary_restricted
                        OemLauncherSupport.EXTRA_SECURITY_GATE -> R.string.set_default_launcher_summary_extra_gate
                        OemLauncherSupport.VERSION_DEPENDENT -> R.string.set_default_launcher_summary_version_dependent
                        OemLauncherSupport.STANDARD -> R.string.set_default_launcher_summary_off
                    }
                )
            },
            R.drawable.ic_settings_category_device,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            action = getString(
                if (isDefaultLauncher) R.string.settings_default_launcher_ready
                else R.string.settings_default_launcher_pending
            )
        ) {
            if (isDefaultLauncher) {
                actionController.clearDefaultLauncher()
            } else {
                actionController.showSetDefaultLauncherDialog()
            }
        }
        defaultLauncherRow.findViewById<ImageView>(R.id.detail_row_icon).apply {
            setImageDrawable(OemLauncherIconLoader.load(activity, launcherProfile))
            imageTintList = null
        }
        addSwitchRow(
            R.string.settings_low_performance_title,
            if (launcherPreferences.isLowPerformanceModeEnabled()) {
                R.string.settings_low_performance_summary_on
            } else {
                R.string.settings_low_performance_summary_off
            },
            R.drawable.ic_settings_permission_battery,
            launcherPreferences.isLowPerformanceModeEnabled()
        ) {
            launcherPreferences.setLowPerformanceModeEnabled(it)
            bind(SettingsScreen.Device)
            overviewController.refreshOverviewUi()
        }
        addRow(
            R.string.settings_icon_scale_title,
            getString(R.string.settings_icon_scale_summary, launcherPreferences.getIconScale()),
            R.drawable.ic_settings_device_scale,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            action = getString(R.string.settings_action_adjust)
        ) {
            showValueDialog(
                title = getString(R.string.settings_icon_scale_dialog_title),
                initial = launcherPreferences.getIconScale(),
                range = LauncherPreferences.MIN_ICON_SCALE..LauncherPreferences.MAX_ICON_SCALE,
                step = 10,
                format = { "$it%" },
                onSave = launcherPreferences::setIconScale
            )
        }
        addRow(
            R.string.settings_dark_mode_title,
            darkModeSummary(),
            R.drawable.ic_settings_device_display,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            action = getString(R.string.settings_action_adjust)
        ) {
            showDarkModeDialog()
        }
        addRow(
            R.string.settings_keep_alive_review_title,
            R.string.settings_keep_alive_review_summary,
            R.drawable.ic_settings_category_permissions,
            R.color.launcher_ginkgo_deep,
            R.color.launcher_ginkgo_soft,
            action = getString(R.string.settings_action_view)
        ) {
            showPermissionGroupDialog(PermissionGroup.KeepAlive)
        }
    }

    private fun bindSystem() = with(activity) {
        addRow(
            R.string.settings_weather_city_title,
            getString(R.string.settings_weather_city_summary, weatherPreferences.getCityName()),
            R.drawable.ic_weather_sun,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            action = getString(R.string.settings_entry_modify).removeSuffix(" ›")
        ) {
            showSetCityDialog()
        }
        addRow(
            R.string.settings_system_title,
            R.string.settings_system_summary,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            action = getString(R.string.settings_action_open)
        ) {
            actionController.openSystemSettings()
        }
        addRow(
            R.string.settings_update_title,
            getString(R.string.settings_update_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            R.drawable.ic_settings_action_update,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            action = getString(R.string.settings_action_check)
        ) {
            checkAppUpdate()
        }
    }

    private fun SettingsActivity.addSwitchRow(
        titleRes: Int,
        summaryRes: Int,
        iconRes: Int,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val row = addRow(
            titleRes,
            summaryRes,
            iconRes,
            if (checked) R.color.launcher_call else R.color.launcher_system,
            if (checked) R.color.launcher_call_soft else R.color.launcher_system_soft
        )
        val toggle = row.findViewById<SettingsToggle>(R.id.detail_row_switch).apply {
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.findViewById<View>(R.id.detail_row_click_target).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { toggle.performClick() }
        }
    }

    private fun SettingsActivity.addRow(
        titleRes: Int,
        summaryRes: Int,
        iconRes: Int,
        tintRes: Int,
        plateRes: Int,
        action: String? = null,
        actionColorRes: Int = R.color.launcher_action_dark,
        chevron: Boolean = false,
        onClick: (() -> Unit)? = null
    ): View = addRow(
        titleRes,
        getString(summaryRes),
        iconRes,
        tintRes,
        plateRes,
        action = action,
        actionColorRes = actionColorRes,
        chevron = chevron,
        onClick = onClick
    )

    private fun SettingsActivity.addRow(
        titleRes: Int,
        summary: String,
        iconRes: Int,
        tintRes: Int,
        plateRes: Int,
        action: String? = null,
        actionColorRes: Int = R.color.launcher_action_dark,
        chevron: Boolean = false,
        onClick: (() -> Unit)? = null
    ): View {
        val container = findViewById<LinearLayout>(R.id.settings_detail_rows)
        val row = layoutInflater.inflate(R.layout.item_settings_detail_row, container, false)
        row.findViewById<TextView>(R.id.detail_row_title).setText(titleRes)
        row.findViewById<TextView>(R.id.detail_row_summary).text = summary
        row.findViewById<ImageView>(R.id.detail_row_icon).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@addRow, tintRes))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@addRow, plateRes))
        }
        row.findViewById<TextView>(R.id.detail_row_action).apply {
            text = action
            setTextColor(ContextCompat.getColor(this@addRow, actionColorRes))
            visibility = if (action == null) View.GONE else View.VISIBLE
            isClickable = onClick != null
            isFocusable = onClick != null
            setOnClickListener { onClick?.invoke() }
        }
        row.findViewById<ImageView>(R.id.detail_row_chevron).visibility =
            if (chevron || action != null) View.VISIBLE else View.GONE
        row.findViewById<View>(R.id.detail_row_click_target).apply {
            isClickable = onClick != null
            isFocusable = onClick != null
            setOnClickListener { onClick?.invoke() }
        }
        container.addView(row)
        return row
    }

    private fun showValueDialog(
        title: String,
        initial: Int,
        range: IntRange,
        step: Int = 1,
        format: (Int) -> String,
        onSave: (Int) -> Unit
    ) {
        var value = initial
        val view = activity.layoutInflater.inflate(R.layout.dialog_settings_value, FrameLayout(activity), false)
        view.findViewById<TextView>(R.id.value_dialog_title).text = title
        val label = view.findViewById<TextView>(R.id.value_dialog_value)
        fun update() {
            label.text = format(value)
        }
        update()
        view.findViewById<View>(R.id.value_dialog_minus).setOnClickListener {
            value = (value - step).coerceAtLeast(range.first)
            update()
        }
        view.findViewById<View>(R.id.value_dialog_plus).setOnClickListener {
            value = (value + step).coerceAtMost(range.last)
            update()
        }
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<View>(R.id.value_dialog_done).setOnClickListener {
            onSave(value)
            dialog.dismiss()
            bind(activity.currentScreen)
            activity.overviewController.refreshOverviewUi()
        }
        dialog.show()
    }

    private fun darkModeSummary(): String = with(activity) {
        getString(
            when (launcherPreferences.getDarkMode()) {
                LauncherPreferences.DARK_MODE_LIGHT -> R.string.settings_dark_mode_summary_light
                LauncherPreferences.DARK_MODE_DARK -> R.string.settings_dark_mode_summary_dark
                else -> R.string.settings_dark_mode_summary_system
            }
        )
    }

    internal fun showDarkModeDialog(): android.app.Dialog {
        val currentMode = activity.launcherPreferences.getDarkMode()
        val options = listOf(
            Triple(
                LauncherPreferences.DARK_MODE_SYSTEM,
                R.string.settings_dark_mode_system,
                R.string.settings_dark_mode_summary_system
            ),
            Triple(
                LauncherPreferences.DARK_MODE_LIGHT,
                R.string.settings_dark_mode_light,
                R.string.settings_dark_mode_summary_light
            ),
            Triple(
                LauncherPreferences.DARK_MODE_DARK,
                R.string.settings_dark_mode_dark,
                R.string.settings_dark_mode_summary_dark
            )
        )
        val dialog = activity.createListDialog(
            title = activity.getString(R.string.settings_dark_mode_title),
            message = activity.getString(R.string.settings_dark_mode_dialog_message)
        )
        options.forEach { (value, titleRes, summaryRes) ->
            val isCurrent = value == currentMode
            activity.addDialogEntry(
                context = dialog,
                title = activity.getString(titleRes),
                summary = activity.getString(summaryRes),
                badge = BadgeStyle(
                    text = activity.getString(
                        if (isCurrent) R.string.status_current else R.string.settings_dark_mode_select
                    ),
                    textColorResId = if (isCurrent) {
                        R.color.launcher_action_dark
                    } else {
                        R.color.launcher_text_secondary
                    },
                    backgroundColorResId = if (isCurrent) {
                        R.color.launcher_call_soft
                    } else {
                        R.color.launcher_surface_soft
                    }
                )
            ) {
                activity.launcherPreferences.setDarkMode(value)
                dialog.dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(
                    when (value) {
                        LauncherPreferences.DARK_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        LauncherPreferences.DARK_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }
        dialog.dialog.show()
        return dialog.dialog
    }

    private fun SettingsScreen.subtitleRes(): Int = when (this) {
        SettingsScreen.Contacts -> R.string.settings_contacts_summary
        SettingsScreen.Calls -> R.string.settings_calls_summary
        SettingsScreen.Permissions -> R.string.settings_permissions_summary
        SettingsScreen.Device -> R.string.settings_device_summary
        SettingsScreen.System -> R.string.settings_system_summary_short
        SettingsScreen.StandardOverview -> R.string.settings_subtitle
        SettingsScreen.ElderOverview -> R.string.settings_elder_subtitle
    }
}
