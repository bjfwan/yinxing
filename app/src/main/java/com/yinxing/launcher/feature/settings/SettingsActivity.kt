package com.yinxing.launcher.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics
import com.yinxing.launcher.feature.incoming.IncomingGuardItem
import com.yinxing.launcher.feature.incoming.IncomingGuardReadiness
import com.yinxing.launcher.feature.incoming.IncomingGuardReadinessEvaluator
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.phone.PhoneContactManager
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class SettingsActivity : AppCompatActivity() {

    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var weatherPreferences: WeatherPreferences

    private lateinit var tvIncomingGuardStatus: TextView
    private lateinit var tvIncomingGuardProgress: TextView
    private lateinit var tvIncomingGuardSummary: TextView
    private lateinit var tvIncomingGuardAction: TextView
    private lateinit var btnIncomingGuardAction: View

    private lateinit var tvContactsHubSummary: TextView
    private lateinit var tvAutoAnswerHubStatus: TextView
    private lateinit var tvAutoAnswerHubSummary: TextView
    private lateinit var tvPermissionHubStatus: TextView
    private lateinit var tvPermissionHubSummary: TextView
    private lateinit var tvDeviceHubStatus: TextView
    private lateinit var tvDeviceHubSummary: TextView
    private lateinit var tvSystemHubSummary: TextView

    private var incomingGuardReadiness = IncomingGuardReadiness(emptyList())
    private var permissionEntryStates = emptyMap<PermissionEntry, PermissionEntryState>()

    private enum class PermissionGroup(
        val titleRes: Int,
        val readySummaryRes: Int,
        val dialogMessageRes: Int
    ) {
        Call(
            R.string.settings_permission_group_call_title,
            R.string.settings_permission_group_call_summary,
            R.string.settings_permission_group_call_dialog_message
        ),
        KeepAlive(
            R.string.settings_permission_group_keep_alive_title,
            R.string.settings_permission_group_keep_alive_summary,
            R.string.settings_permission_group_keep_alive_dialog_message
        ),
        Video(
            R.string.settings_permission_group_video_title,
            R.string.settings_permission_group_video_summary,
            R.string.settings_permission_group_video_dialog_message
        );

        val entries: List<PermissionEntry>
            get() = when (this) {
                Call -> listOf(
                    PermissionEntry.PhonePermission,
                    PermissionEntry.NotificationPermission
                )
                KeepAlive -> listOf(
                    PermissionEntry.DefaultLauncher,
                    PermissionEntry.BatteryOptimization,
                    PermissionEntry.AutoStart,
                    PermissionEntry.BackgroundStart
                )
                Video -> listOf(
                    PermissionEntry.Accessibility,
                    PermissionEntry.Overlay
                )
            }
    }

    private enum class PermissionEntry {
        PhonePermission,
        NotificationPermission,
        DefaultLauncher,
        BatteryOptimization,
        AutoStart,
        BackgroundStart,
        Accessibility,
        Overlay
    }

    private data class PermissionEntryState(
        val entry: PermissionEntry,
        val isReady: Boolean,
        val requiresManualConfirmation: Boolean = false
    )

    private data class BadgeStyle(
        val text: String,
        val textColorResId: Int,
        val backgroundColorResId: Int
    )

    private data class ListSheetContext(
        val dialog: BottomSheetDialog,
        val contentView: View,
        val container: LinearLayout
    )

    private data class GroupRenderState(
        val summary: String,
        val badge: BadgeStyle
    )

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            Toast.makeText(
                this,
                getString(R.string.settings_phone_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            PermissionUtil.openAppDetailSettings(this)
        }
        refreshOverviewUi()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(
                this,
                getString(R.string.settings_notification_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            PermissionUtil.openNotificationSettings(this)
        }
        refreshOverviewUi()
    }

    private val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshOverviewUi()
        if (isDefaultLauncher()) {
            Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
        } else {
            openDefaultLauncherSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        launcherPreferences = LauncherPreferences.getInstance(this)
        weatherPreferences = WeatherPreferences.getInstance(this)

        bindOverviewViews()
        bindOverviewActions()
    }

    override fun onResume() {
        super.onResume()
        refreshOverviewUi()
    }

    private fun bindOverviewViews() {
        tvIncomingGuardStatus = findViewById(R.id.tv_incoming_guard_status)
        tvIncomingGuardProgress = findViewById(R.id.tv_incoming_guard_progress)
        tvIncomingGuardSummary = findViewById(R.id.tv_incoming_guard_summary)
        tvIncomingGuardAction = findViewById(R.id.tv_incoming_guard_action)
        btnIncomingGuardAction = findViewById(R.id.btn_incoming_guard_action)

        tvContactsHubSummary = findViewById(R.id.tv_contacts_hub_summary)
        tvAutoAnswerHubStatus = findViewById(R.id.tv_auto_answer_hub_status)
        tvAutoAnswerHubSummary = findViewById(R.id.tv_auto_answer_hub_summary)
        tvPermissionHubStatus = findViewById(R.id.tv_permission_hub_status)
        tvPermissionHubSummary = findViewById(R.id.tv_permission_hub_summary)
        tvDeviceHubStatus = findViewById(R.id.tv_device_hub_status)
        tvDeviceHubSummary = findViewById(R.id.tv_device_hub_summary)
        tvSystemHubSummary = findViewById(R.id.tv_system_hub_summary)
    }

    private fun bindOverviewActions() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_card_incoming_guard).setOnClickListener { showIncomingGuardSheet() }
        btnIncomingGuardAction.setOnClickListener { showIncomingGuardSheet() }
        findViewById<View>(R.id.btn_card_contacts).setOnClickListener { showContactsSheet() }
        findViewById<View>(R.id.btn_card_auto_answer).setOnClickListener { showAutoAnswerSheet() }
        findViewById<View>(R.id.btn_card_permissions).setOnClickListener { showPermissionGroupsSheet() }
        findViewById<View>(R.id.btn_card_device).setOnClickListener { showDeviceSettingsSheet() }
        findViewById<View>(R.id.btn_card_system).setOnClickListener { showSystemSheet() }
    }

    private fun refreshOverviewUi() {
        updateContactsHubSummary()
        updateAutoAnswerHubCard()
        updateSystemHubCard()
        refreshAllPermissionUi()
    }

    private fun updateContactsHubSummary() {
        val phoneCount = PhoneContactManager.getInstance(this).getContacts().size
        val videoCount = ContactManager.getInstance(this).getContacts().size
        val homeAppCount = launcherPreferences.getSelectedPackages().size
        tvContactsHubSummary.text = getString(
            R.string.settings_contacts_hub_summary,
            phoneCount,
            videoCount,
            homeAppCount
        )
    }

    private fun updateAutoAnswerHubCard() {
        val enabled = launcherPreferences.isAutoAnswerEnabled()
        if (enabled) {
            applyInfoBadge(
                tv = tvAutoAnswerHubStatus,
                text = getString(R.string.settings_guard_status_done),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
            tvAutoAnswerHubSummary.text = getString(
                R.string.settings_auto_answer_delay_summary,
                launcherPreferences.getAutoAnswerDelaySeconds()
            )
        } else {
            applyInfoBadge(
                tv = tvAutoAnswerHubStatus,
                text = getString(R.string.settings_guard_status_pending),
                textColorResId = R.color.launcher_warning,
                backgroundColorResId = R.color.launcher_warning_soft
            )
            tvAutoAnswerHubSummary.text = getString(R.string.settings_auto_answer_summary_off)
        }
    }

    private fun updateSystemHubCard() {
        tvSystemHubSummary.text = getString(
            R.string.settings_weather_city_summary,
            weatherPreferences.getCityName()
        )
    }

    private fun refreshAllPermissionUi() {
        val accessibilityGranted = PermissionUtil.isAnyAccessibilityServiceEnabled(this)
        val overlayGranted = PermissionUtil.canDrawOverlays(this)
        refreshIncomingGuardUi()
        permissionEntryStates = buildPermissionEntryStates(
            accessibilityGranted = accessibilityGranted,
            overlayGranted = overlayGranted
        )
        refreshPermissionHubCard()
        refreshDeviceHubCard()
    }

    private fun refreshIncomingGuardUi() {
        incomingGuardReadiness = IncomingGuardReadinessEvaluator.evaluate(
            hasPhonePermission = PermissionUtil.hasPhonePermission(this),
            hasNotificationPermission = PermissionUtil.hasNotificationPermission(this),
            isDefaultLauncher = isDefaultLauncher(),
            ignoresBatteryOptimizations = PermissionUtil.isIgnoringBatteryOptimizations(this),
            autoStartConfirmed = launcherPreferences.isAutoStartConfirmed(),
            backgroundStartConfirmed = launcherPreferences.isBackgroundStartConfirmed()
        )
        val blocker = incomingGuardReadiness.blocker?.item

        tvIncomingGuardProgress.text = getString(
            R.string.settings_incoming_guard_progress,
            incomingGuardReadiness.completedCount
        )

        if (incomingGuardReadiness.isReady) {
            applyInfoBadge(
                tv = tvIncomingGuardStatus,
                text = getString(R.string.settings_incoming_guard_status_ready),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
            tvIncomingGuardSummary.text = getString(R.string.settings_incoming_guard_summary_ready)
            tvIncomingGuardAction.text = getString(R.string.settings_incoming_guard_action_open)
        } else {
            val blockerTitle = blocker?.let(::guardTitle).orEmpty()
            applyInfoBadge(
                tv = tvIncomingGuardStatus,
                text = getString(R.string.settings_incoming_guard_status_pending),
                textColorResId = R.color.launcher_warning,
                backgroundColorResId = R.color.launcher_warning_soft
            )
            tvIncomingGuardSummary.text = getString(
                R.string.settings_incoming_guard_summary_blocked,
                blockerTitle
            )
            tvIncomingGuardAction.text = getString(
                R.string.settings_incoming_guard_action_fix,
                blockerTitle
            )
        }
    }

    private fun buildPermissionEntryStates(
        accessibilityGranted: Boolean,
        overlayGranted: Boolean
    ): Map<PermissionEntry, PermissionEntryState> {
        val states = linkedMapOf<PermissionEntry, PermissionEntryState>()
        incomingGuardReadiness.items.forEach { itemState ->
            val entry = itemState.item.toPermissionEntry()
            states[entry] = PermissionEntryState(
                entry = entry,
                isReady = itemState.isReady,
                requiresManualConfirmation = itemState.requiresManualConfirmation
            )
        }
        states[PermissionEntry.Accessibility] = PermissionEntryState(
            entry = PermissionEntry.Accessibility,
            isReady = accessibilityGranted
        )
        states[PermissionEntry.Overlay] = PermissionEntryState(
            entry = PermissionEntry.Overlay,
            isReady = overlayGranted
        )
        return states
    }

    private fun refreshPermissionHubCard() {
        val states = permissionEntryStates.values.toList()
        val blocker = states.firstOrNull { !it.isReady }
        val completedCount = states.count { it.isReady }
        tvPermissionHubSummary.text = if (blocker == null) {
            getString(R.string.settings_permissions_hub_summary_ready)
        } else {
            getString(
                R.string.settings_permissions_hub_summary_pending,
                permissionEntryTitle(blocker.entry)
            )
        }
        applyInfoBadge(
            tv = tvPermissionHubStatus,
            text = getString(R.string.settings_permission_group_progress, completedCount, states.size),
            textColorResId = if (blocker == null) R.color.launcher_action_dark else R.color.launcher_warning,
            backgroundColorResId = if (blocker == null) R.color.launcher_primary_soft else R.color.launcher_warning_soft
        )
    }

    private fun refreshDeviceHubCard() {
        val defaultSummary = if (isDefaultLauncher()) {
            getString(R.string.settings_device_hub_default_ready)
        } else {
            getString(R.string.settings_device_hub_default_pending)
        }
        val kioskSummary = if (launcherPreferences.isKioskModeEnabled()) {
            getString(R.string.settings_device_hub_kiosk_on)
        } else {
            getString(R.string.settings_device_hub_kiosk_off)
        }
        val performanceSummary = if (launcherPreferences.isLowPerformanceModeEnabled()) {
            getString(R.string.settings_device_hub_low_performance_on)
        } else {
            getString(R.string.settings_device_hub_low_performance_off)
        }
        tvDeviceHubSummary.text = getString(
            R.string.settings_device_hub_summary,
            defaultSummary,
            kioskSummary,
            performanceSummary
        )
        applyInfoBadge(
            tv = tvDeviceHubStatus,
            text = if (isDefaultLauncher()) {
                getString(R.string.settings_guard_status_done)
            } else {
                getString(R.string.settings_guard_status_pending)
            },
            textColorResId = if (isDefaultLauncher()) R.color.launcher_action_dark else R.color.launcher_warning,
            backgroundColorResId = if (isDefaultLauncher()) R.color.launcher_primary_soft else R.color.launcher_warning_soft
        )
    }

    private fun showIncomingGuardSheet() {
        val blocker = incomingGuardReadiness.blocker?.item
        val message = if (incomingGuardReadiness.isReady) {
            getString(R.string.settings_incoming_guard_summary_ready)
        } else {
            getString(
                R.string.settings_incoming_guard_summary_blocked,
                blocker?.let(::guardTitle).orEmpty()
            )
        }
        val sheet = createListSheet(
            title = getString(R.string.settings_section_incoming_guard_title),
            message = message
        )

        incomingGuardReadiness.items.forEach { itemState ->
            val entry = itemState.item.toPermissionEntry()
            val state = permissionEntryStates[entry] ?: return@forEach
            val badge = when {
                itemState.isReady && itemState.requiresManualConfirmation -> BadgeStyle(
                    text = getString(R.string.settings_guard_status_confirmed),
                    textColorResId = R.color.launcher_action_dark,
                    backgroundColorResId = R.color.launcher_primary_soft
                )
                itemState.isReady -> BadgeStyle(
                    text = getString(R.string.settings_guard_status_done),
                    textColorResId = R.color.launcher_action_dark,
                    backgroundColorResId = R.color.launcher_primary_soft
                )
                incomingGuardReadiness.blocker?.item == itemState.item -> BadgeStyle(
                    text = getString(R.string.settings_guard_status_blocking),
                    textColorResId = R.color.launcher_warning,
                    backgroundColorResId = R.color.launcher_warning_soft
                )
                else -> permissionEntryBadge(state)
            }
            addSheetEntry(
                context = sheet,
                title = guardTitle(itemState.item),
                summary = permissionEntrySummary(state),
                badge = badge
            ) {
                sheet.dialog.dismiss()
                openIncomingGuardItem(itemState.item)
            }
        }
        sheet.dialog.show()
    }

    private fun showContactsSheet() {
        val sheet = createListSheet(
            title = getString(R.string.settings_section_quick_setup_title),
            message = getString(R.string.settings_sheet_contacts_message)
        )
        val phoneCount = PhoneContactManager.getInstance(this).getContacts().size
        val videoCount = ContactManager.getInstance(this).getContacts().size
        val homeAppCount = launcherPreferences.getSelectedPackages().size

        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_manage_phone_contacts_title),
            summary = getString(R.string.settings_contacts_phone_count, phoneCount),
            badge = actionBadge(getString(R.string.settings_manage_action))
        ) {
            sheet.dialog.dismiss()
            startActivity(PhoneContactActivity.createIntent(this, startInManageMode = true))
        }
        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_manage_video_contacts_title),
            summary = getString(R.string.settings_contacts_video_count, videoCount),
            badge = actionBadge(getString(R.string.settings_manage_action))
        ) {
            sheet.dialog.dismiss()
            startActivity(VideoCallActivity.createIntent(this, startInManageMode = true))
        }
        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_manage_home_apps_title),
            summary = getString(R.string.settings_home_apps_count, homeAppCount),
            badge = actionBadge(getString(R.string.settings_manage_action))
        ) {
            sheet.dialog.dismiss()
            startActivity(Intent(this, AppManageActivity::class.java))
        }
        sheet.dialog.show()
    }

    private fun showAutoAnswerSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_settings_auto_answer, null)
        dialog.setContentView(view)

        val autoAnswerSwitch = view.findViewById<SwitchCompat>(R.id.switch_auto_answer_sheet)
        val autoAnswerSummary = view.findViewById<TextView>(R.id.tv_auto_answer_sheet_summary)
        val autoAnswerDelaySummary = view.findViewById<TextView>(R.id.tv_auto_answer_delay_sheet_summary)
        val autoAnswerDelayMinus = view.findViewById<View>(R.id.btn_auto_answer_delay_sheet_minus)
        val autoAnswerDelayPlus = view.findViewById<View>(R.id.btn_auto_answer_delay_sheet_plus)
        val incomingTraceSummary = view.findViewById<TextView>(R.id.tv_incoming_trace_sheet_summary)

        fun updateDelayControls(enabled: Boolean) {
            val alpha = if (enabled) 1f else 0.38f
            listOf(autoAnswerDelayMinus, autoAnswerDelayPlus, autoAnswerDelaySummary).forEach { target ->
                target.isEnabled = enabled
                target.alpha = alpha
            }
        }

        fun updateSheetSummary() {
            val enabled = launcherPreferences.isAutoAnswerEnabled()
            autoAnswerSwitch.isChecked = enabled
            autoAnswerSummary.text = getString(
                if (enabled) R.string.settings_auto_answer_summary_on
                else R.string.settings_auto_answer_summary_off
            )
            autoAnswerDelaySummary.text = getString(
                R.string.settings_auto_answer_delay_summary,
                launcherPreferences.getAutoAnswerDelaySeconds()
            )
            incomingTraceSummary.text = IncomingCallDiagnostics.getDisplayText(this)
            updateDelayControls(enabled)
        }

        updateSheetSummary()

        autoAnswerSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPreferences.setAutoAnswerEnabled(isChecked)
            updateSheetSummary()
            updateAutoAnswerHubCard()
        }
        autoAnswerDelayMinus.setOnClickListener {
            if (!launcherPreferences.isAutoAnswerEnabled()) return@setOnClickListener
            val updated = launcherPreferences.getAutoAnswerDelaySeconds() - 1
            launcherPreferences.setAutoAnswerDelaySeconds(updated)
            updateSheetSummary()
            updateAutoAnswerHubCard()
        }
        autoAnswerDelayPlus.setOnClickListener {
            if (!launcherPreferences.isAutoAnswerEnabled()) return@setOnClickListener
            val updated = launcherPreferences.getAutoAnswerDelaySeconds() + 1
            launcherPreferences.setAutoAnswerDelaySeconds(updated)
            updateSheetSummary()
            updateAutoAnswerHubCard()
        }
        view.findViewById<View>(R.id.btn_close_auto_answer_sheet).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showPermissionGroupsSheet() {
        val sheet = createListSheet(
            title = getString(R.string.settings_section_support_title),
            message = getString(R.string.settings_sheet_permissions_message)
        )
        PermissionGroup.entries.forEach { group ->
            val renderState = permissionGroupRenderState(group)
            addSheetEntry(
                context = sheet,
                title = getString(group.titleRes),
                summary = renderState.summary,
                badge = renderState.badge
            ) {
                sheet.dialog.dismiss()
                showPermissionGroupDialog(group)
            }
        }
        sheet.dialog.show()
    }

    private fun showDeviceSettingsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_settings_device, null)
        dialog.setContentView(view)

        val defaultLauncherSummary = view.findViewById<TextView>(R.id.tv_default_launcher_sheet_summary)
        val clearDefaultLauncherSummary = view.findViewById<TextView>(R.id.tv_clear_default_launcher_sheet_summary)
        val kioskModeSwitch = view.findViewById<SwitchCompat>(R.id.switch_kiosk_mode_sheet)
        val kioskModeSummary = view.findViewById<TextView>(R.id.tv_kiosk_mode_sheet_summary)
        val lowPerformanceSwitch = view.findViewById<SwitchCompat>(R.id.switch_low_performance_sheet)
        val lowPerformanceSummary = view.findViewById<TextView>(R.id.tv_low_performance_sheet_summary)
        val iconScaleSeekBar = view.findViewById<android.widget.SeekBar>(R.id.seekbar_icon_scale)
        val iconScaleValue = view.findViewById<TextView>(R.id.tv_icon_scale_value)

        iconScaleSeekBar.progress = launcherPreferences.getIconScale() - LauncherPreferences.MIN_ICON_SCALE
        iconScaleValue.text = getString(R.string.settings_icon_scale_summary, launcherPreferences.getIconScale())
        iconScaleSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress + LauncherPreferences.MIN_ICON_SCALE
                iconScaleValue.text = getString(R.string.settings_icon_scale_summary, scale)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val scale = (seekBar?.progress ?: 0) + LauncherPreferences.MIN_ICON_SCALE
                launcherPreferences.setIconScale(scale)
            }
        })

        fun updateSheetState() {
            val isDefault = isDefaultLauncher()
            defaultLauncherSummary.text = getString(
                if (isDefault) R.string.set_default_launcher_summary_on
                else R.string.set_default_launcher_summary_off
            )
            clearDefaultLauncherSummary.text = getString(
                if (isDefault) R.string.clear_default_launcher_summary_on
                else R.string.clear_default_launcher_summary_off
            )
            kioskModeSwitch.isChecked = launcherPreferences.isKioskModeEnabled()
            kioskModeSummary.text = getString(
                if (launcherPreferences.isKioskModeEnabled()) R.string.settings_kiosk_mode_summary_on
                else R.string.settings_kiosk_mode_summary_off
            )
            lowPerformanceSwitch.isChecked = launcherPreferences.isLowPerformanceModeEnabled()
            lowPerformanceSummary.text = getString(
                if (launcherPreferences.isLowPerformanceModeEnabled()) R.string.settings_low_performance_summary_on
                else R.string.settings_low_performance_summary_off
            )
        }

        updateSheetState()

        view.findViewById<View>(R.id.btn_set_default_launcher_sheet).setOnClickListener {
            dialog.dismiss()
            if (isDefaultLauncher()) {
                Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
            } else {
                showSetDefaultLauncherDialog()
            }
        }

        view.findViewById<View>(R.id.btn_clear_default_launcher_sheet).setOnClickListener {
            dialog.dismiss()
            clearDefaultLauncher()
        }

        kioskModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isDefaultLauncher()) {
                kioskModeSwitch.isChecked = false
                Toast.makeText(
                    this,
                    getString(R.string.settings_kiosk_mode_requires_default_launcher),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnCheckedChangeListener
            }
            launcherPreferences.setKioskModeEnabled(isChecked)
            updateSheetState()
            refreshDeviceHubCard()
        }
        lowPerformanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPreferences.setLowPerformanceModeEnabled(isChecked)
            updateSheetState()
            refreshDeviceHubCard()
        }
        view.findViewById<View>(R.id.btn_keep_alive_review_sheet).setOnClickListener {
            dialog.dismiss()
            showPermissionGroupDialog(PermissionGroup.KeepAlive)
        }
        view.findViewById<View>(R.id.btn_close_device_sheet).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showSystemSheet() {
        val sheet = createListSheet(
            title = getString(R.string.settings_section_system_title),
            message = getString(R.string.settings_sheet_system_message)
        )
        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_weather_city_title),
            summary = getString(R.string.settings_weather_city_summary, weatherPreferences.getCityName()),
            badge = actionBadge(getString(R.string.settings_entry_modify))
        ) {
            sheet.dialog.dismiss()
            showSetCityDialog()
        }
        addSheetEntry(
            context = sheet,
            title = getString(R.string.settings_system_title),
            summary = getString(R.string.settings_system_summary),
            badge = actionBadge(getString(R.string.settings_entry_open_settings))
        ) {
            sheet.dialog.dismiss()
            openSystemSettings()
        }
        sheet.dialog.show()
    }

    private fun createListSheet(title: String, message: String): ListSheetContext {
        val dialog = BottomSheetDialog(this)
        val contentView = layoutInflater.inflate(R.layout.dialog_permission_group, null)
        contentView.findViewById<TextView>(R.id.tv_dialog_title).text = title
        contentView.findViewById<TextView>(R.id.tv_dialog_message).text = message
        contentView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(contentView)
        return ListSheetContext(
            dialog = dialog,
            contentView = contentView,
            container = contentView.findViewById(R.id.layout_permission_items)
        )
    }

    private fun addSheetEntry(
        context: ListSheetContext,
        title: String,
        summary: String,
        badge: BadgeStyle,
        onClick: () -> Unit
    ) {
        val itemView = layoutInflater.inflate(R.layout.item_settings_permission_entry, context.container, false)
        itemView.findViewById<TextView>(R.id.tv_permission_item_title).text = title
        itemView.findViewById<TextView>(R.id.tv_permission_item_summary).text = summary
        applyInfoBadge(
            tv = itemView.findViewById(R.id.tv_permission_item_status),
            text = badge.text,
            textColorResId = badge.textColorResId,
            backgroundColorResId = badge.backgroundColorResId
        )
        itemView.setOnClickListener { onClick() }
        context.container.addView(itemView)
    }

    private fun actionBadge(text: String): BadgeStyle {
        return BadgeStyle(
            text = text,
            textColorResId = R.color.launcher_action,
            backgroundColorResId = R.color.launcher_surface_muted
        )
    }

    private fun permissionGroupRenderState(group: PermissionGroup): GroupRenderState {
        val states = group.entries.mapNotNull(permissionEntryStates::get)
        val blocker = states.firstOrNull { !it.isReady }
        val completedCount = states.count { it.isReady }
        val summary = if (blocker == null) {
            getString(group.readySummaryRes)
        } else {
            getString(
                R.string.settings_permission_group_pending_summary,
                permissionEntryTitle(blocker.entry)
            )
        }
        val badge = if (blocker == null) {
            BadgeStyle(
                text = getString(R.string.settings_guard_status_done),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
        } else {
            BadgeStyle(
                text = getString(R.string.settings_permission_group_progress, completedCount, states.size),
                textColorResId = R.color.launcher_warning,
                backgroundColorResId = R.color.launcher_warning_soft
            )
        }
        return GroupRenderState(summary = summary, badge = badge)
    }

    private fun showPermissionGroupDialog(group: PermissionGroup) {
        val sheet = createListSheet(
            title = getString(group.titleRes),
            message = getString(group.dialogMessageRes)
        )
        group.entries.mapNotNull(permissionEntryStates::get).forEach { state ->
            addSheetEntry(
                context = sheet,
                title = permissionEntryTitle(state.entry),
                summary = permissionEntrySummary(state),
                badge = permissionEntryBadge(state)
            ) {
                sheet.dialog.dismiss()
                openPermissionEntry(state.entry)
            }
        }
        sheet.dialog.show()
    }

    private fun permissionEntrySummary(state: PermissionEntryState): String {
        return when (state.entry) {
            PermissionEntry.PhonePermission -> getString(
                if (state.isReady) R.string.settings_phone_permission_summary_on
                else R.string.settings_phone_permission_summary_off
            )
            PermissionEntry.NotificationPermission -> getString(
                if (state.isReady) R.string.settings_notification_permission_summary_on
                else R.string.settings_notification_permission_summary_off
            )
            PermissionEntry.DefaultLauncher -> getString(
                if (state.isReady) R.string.set_default_launcher_summary_on
                else R.string.set_default_launcher_summary_off
            )
            PermissionEntry.BatteryOptimization -> getString(
                if (state.isReady) R.string.settings_battery_summary_on
                else R.string.settings_battery_summary_off
            )
            PermissionEntry.AutoStart -> getString(
                if (state.isReady) R.string.settings_autostart_summary_on
                else R.string.settings_autostart_summary_off
            )
            PermissionEntry.BackgroundStart -> getString(
                if (state.isReady) R.string.settings_bg_start_summary_on
                else R.string.settings_bg_start_summary_off
            )
            PermissionEntry.Accessibility -> getString(
                if (state.isReady) R.string.settings_accessibility_summary_on
                else R.string.settings_accessibility_summary_off
            )
            PermissionEntry.Overlay -> getString(
                if (state.isReady) R.string.settings_overlay_summary_on
                else R.string.settings_overlay_summary_off
            )
        }
    }

    private fun permissionEntryTitle(entry: PermissionEntry): String {
        return getString(
            when (entry) {
                PermissionEntry.PhonePermission -> R.string.settings_phone_permission_title
                PermissionEntry.NotificationPermission -> R.string.settings_notification_permission_title
                PermissionEntry.DefaultLauncher -> R.string.set_default_launcher_title
                PermissionEntry.BatteryOptimization -> R.string.settings_battery_title
                PermissionEntry.AutoStart -> R.string.settings_autostart_title
                PermissionEntry.BackgroundStart -> R.string.settings_bg_start_title
                PermissionEntry.Accessibility -> R.string.settings_accessibility_title
                PermissionEntry.Overlay -> R.string.settings_overlay_title
            }
        )
    }

    private fun permissionEntryBadge(state: PermissionEntryState): BadgeStyle {
        return when {
            state.isReady && state.requiresManualConfirmation -> BadgeStyle(
                text = getString(R.string.settings_guard_status_confirmed),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
            state.isReady -> BadgeStyle(
                text = getString(R.string.settings_guard_status_done),
                textColorResId = R.color.launcher_action_dark,
                backgroundColorResId = R.color.launcher_primary_soft
            )
            state.requiresManualConfirmation -> BadgeStyle(
                text = getString(R.string.settings_guard_status_pending),
                textColorResId = R.color.launcher_warning,
                backgroundColorResId = R.color.launcher_warning_soft
            )
            else -> BadgeStyle(
                text = getString(R.string.settings_permission_go_set),
                textColorResId = R.color.launcher_action,
                backgroundColorResId = R.color.launcher_surface_muted
            )
        }
    }

    private fun openPermissionEntry(entry: PermissionEntry) {
        when (entry) {
            PermissionEntry.PhonePermission -> requestPhonePermissions()
            PermissionEntry.NotificationPermission -> requestNotificationPermission()
            PermissionEntry.DefaultLauncher -> openIncomingGuardItem(IncomingGuardItem.DefaultLauncher)
            PermissionEntry.BatteryOptimization -> openIncomingGuardItem(IncomingGuardItem.BatteryOptimization)
            PermissionEntry.AutoStart -> openIncomingGuardItem(IncomingGuardItem.AutoStart)
            PermissionEntry.BackgroundStart -> openIncomingGuardItem(IncomingGuardItem.BackgroundStart)
            PermissionEntry.Accessibility -> PermissionUtil.openAccessibilitySettings(this)
            PermissionEntry.Overlay -> PermissionUtil.openOverlaySettings(this)
        }
    }

    private fun guardTitle(item: IncomingGuardItem): String {
        return permissionEntryTitle(item.toPermissionEntry())
    }

    private fun IncomingGuardItem.toPermissionEntry(): PermissionEntry {
        return when (this) {
            IncomingGuardItem.PhonePermission -> PermissionEntry.PhonePermission
            IncomingGuardItem.NotificationPermission -> PermissionEntry.NotificationPermission
            IncomingGuardItem.DefaultLauncher -> PermissionEntry.DefaultLauncher
            IncomingGuardItem.BatteryOptimization -> PermissionEntry.BatteryOptimization
            IncomingGuardItem.AutoStart -> PermissionEntry.AutoStart
            IncomingGuardItem.BackgroundStart -> PermissionEntry.BackgroundStart
        }
    }

    private fun openIncomingGuardItem(item: IncomingGuardItem) {
        when (item) {
            IncomingGuardItem.PhonePermission -> requestPhonePermissions()
            IncomingGuardItem.NotificationPermission -> requestNotificationPermission()
            IncomingGuardItem.DefaultLauncher -> {
                if (isDefaultLauncher()) {
                    Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
                } else {
                    showSetDefaultLauncherDialog()
                }
            }
            IncomingGuardItem.BatteryOptimization -> PermissionUtil.openBatteryOptimizationSettings(this)
            IncomingGuardItem.AutoStart -> showManualCheckDialog(IncomingGuardItem.AutoStart)
            IncomingGuardItem.BackgroundStart -> showManualCheckDialog(IncomingGuardItem.BackgroundStart)
        }
    }

    private fun requestPhonePermissions() {
        if (PermissionUtil.hasPhonePermission(this)) {
            Toast.makeText(
                this,
                getString(R.string.settings_phone_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
        }
        phonePermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtil.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermissionUtil.openNotificationSettings(this)
        }
    }

    private fun showManualCheckDialog(item: IncomingGuardItem) {
        val title = guardTitle(item)
        val confirmed = when (item) {
            IncomingGuardItem.AutoStart -> launcherPreferences.isAutoStartConfirmed()
            IncomingGuardItem.BackgroundStart -> launcherPreferences.isBackgroundStartConfirmed()
            else -> false
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = title
        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text =
            getString(R.string.settings_manual_check_message, title)
        dialogView.findViewById<TextView>(R.id.tv_cancel_label).text = getString(
            if (confirmed) R.string.settings_manual_check_mark_pending
            else R.string.settings_manual_check_mark_done
        )
        dialogView.findViewById<TextView>(R.id.tv_primary_label).text =
            getString(R.string.action_go_to_settings)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            setManualGuardConfirmation(item, !confirmed)
        }
        dialogView.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
            dialog.dismiss()
            when (item) {
                IncomingGuardItem.AutoStart -> PermissionUtil.openAutoStartSettings(this)
                IncomingGuardItem.BackgroundStart -> PermissionUtil.openBackgroundStartSettings(this)
                else -> Unit
            }
        }
        dialog.show()
    }

    private fun setManualGuardConfirmation(item: IncomingGuardItem, confirmed: Boolean) {
        when (item) {
            IncomingGuardItem.AutoStart -> launcherPreferences.setAutoStartConfirmed(confirmed)
            IncomingGuardItem.BackgroundStart -> launcherPreferences.setBackgroundStartConfirmed(confirmed)
            else -> return
        }
        Toast.makeText(
            this,
            getString(
                if (confirmed) R.string.settings_manual_check_done_toast
                else R.string.settings_manual_check_reset_toast,
                guardTitle(item)
            ),
            Toast.LENGTH_SHORT
        ).show()
        refreshOverviewUi()
    }

    private fun applyInfoBadge(
        tv: TextView,
        text: String,
        textColorResId: Int,
        backgroundColorResId: Int
    ) {
        tv.text = text
        tv.setTextColor(getColor(textColorResId))
        tv.setBackgroundResource(R.drawable.edit_text_background)
        tv.backgroundTintList = ColorStateList.valueOf(getColor(backgroundColorResId))
    }

    private fun showSetCityDialog() {
        val currentCity = weatherPreferences.getCityName()
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_city, null)
        val etCity = dialogView.findViewById<EditText>(R.id.et_city)
        etCity.setText(currentCity)
        etCity.setSelection(currentCity.length)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_cancel)
            .setOnClickListener { dialog.dismiss() }

        val confirm = {
            val city = etCity.text.toString().trim()
            if (city.isNotEmpty()) {
                dialog.dismiss()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etCity.windowToken, 0)
                weatherPreferences.setCityName(city)
                WeatherRepository.clearCache()
                updateSystemHubCard()
                Toast.makeText(
                    this,
                    getString(R.string.settings_weather_city_updated, city),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this, getString(R.string.settings_weather_city_empty), Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_confirm)
            .setOnClickListener { confirm() }

        etCity.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirm()
                true
            } else {
                false
            }
        }

        dialog.show()
        etCity.postDelayed({
            etCity.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etCity, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun showSetDefaultLauncherDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = getString(R.string.set_default_launcher_title)
        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = getString(R.string.set_default_launcher_message)
        dialogView.findViewById<TextView>(R.id.tv_cancel_label).text = getString(R.string.set_default_launcher_later)
        dialogView.findViewById<TextView>(R.id.tv_primary_label).text = getString(R.string.set_default_launcher_action)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
            dialog.dismiss()
            requestDefaultLauncherRole()
        }
        dialog.show()
    }

    private fun clearDefaultLauncher() {
        if (!isDefaultLauncher()) {
            Toast.makeText(this, getString(R.string.clear_default_launcher_summary_off), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            @Suppress("DEPRECATION")
            packageManager.clearPackagePreferredActivities(packageName)
        } catch (_: Exception) {
        }
        openDefaultLauncherSettings()
        Toast.makeText(this, "请在系统设置中选择其他桌面应用", Toast.LENGTH_LONG).show()
    }

    private fun requestDefaultLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requestDefaultLauncherRoleByRoleManager()) {
            return
        }
        openDefaultLauncherSettings()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun requestDefaultLauncherRoleByRoleManager(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return false
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            refreshOverviewUi()
            Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
            return true
        }
        return runCatching {
            defaultLauncherRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            true
        }.getOrDefault(false)
    }

    private fun openDefaultLauncherSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                Log.w("SettingsActivity", "openDefaultLauncherSettings failed for ${intent.action}")
            }
        }
        Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
    }
}
