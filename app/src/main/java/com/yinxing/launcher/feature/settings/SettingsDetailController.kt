package com.yinxing.launcher.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import com.google.android.accessibility.selecttospeak.WeChatTeachingPrepareResult
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingFingerprint
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingFingerprintFactory
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStep
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStore
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterSetting
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.common.lobster.LobsterTrace
import com.yinxing.launcher.common.lobster.LobsterUserReportFactory
import com.yinxing.launcher.common.lobster.LobsterUserReportType
import com.yinxing.launcher.common.lobster.withTrace
import com.yinxing.launcher.common.util.EmergencyContactNumber
import com.yinxing.launcher.common.util.AccessibilityServiceMatcher
import com.yinxing.launcher.common.util.OemLauncherIconLoader
import com.yinxing.launcher.common.util.OemLauncherPolicy
import com.yinxing.launcher.common.util.OemLauncherSupport
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.feature.fall.FallDetectionService
import com.yinxing.launcher.feature.appmanage.AppManageActivity
import com.yinxing.launcher.feature.incoming.DefaultPhoneRoleController
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics
import com.yinxing.launcher.feature.incoming.OemIncomingCallPolicy
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.videocall.VideoCallActivity
import kotlin.math.roundToInt

internal class SettingsDetailController(private val activity: SettingsActivity) {
    private var activeRowsContainerId = R.id.settings_detail_rows
    private var activeAboutTab = AboutTab.PROJECT

    private enum class AboutTab {
        PROJECT,
        EVIDENCE,
        SERVICE
    }

    fun bind(screen: SettingsScreen) {
        if (screen in setOf(SettingsScreen.StandardOverview, SettingsScreen.ElderOverview)) return
        activity.findViewById<View>(R.id.btn_detail_back).setOnClickListener {
            activity.navigateBack()
        }
        activity.findViewById<TextView>(R.id.settings_detail_page_title).setText(screen.titleRes)
        activity.findViewById<TextView>(R.id.settings_detail_page_subtitle).setText(screen.subtitleRes())
        activeRowsContainerId = R.id.settings_detail_rows
        activity.findViewById<View>(R.id.settings_about_hero).visibility = View.GONE
        activity.findViewById<View>(R.id.settings_about_tabs).visibility = View.GONE
        activity.findViewById<View>(R.id.settings_detail_card_secondary).visibility = View.GONE
        activity.findViewById<View>(R.id.settings_detail_card_tertiary).visibility = View.GONE
        activity.findViewById<LinearLayout>(R.id.settings_detail_rows).removeAllViews()
        activity.findViewById<LinearLayout>(R.id.settings_detail_rows_secondary).removeAllViews()
        activity.findViewById<LinearLayout>(R.id.settings_detail_rows_tertiary).removeAllViews()

        when (screen) {
            SettingsScreen.Contacts -> bindContacts()
            SettingsScreen.WeChatRules -> bindWechatRules()
            SettingsScreen.Calls -> bindCalls()
            SettingsScreen.CallDiagnostics -> bindCallDiagnostics()
            SettingsScreen.Safety -> bindSafety()
            SettingsScreen.Permissions -> bindPermissions()
            SettingsScreen.Background -> bindBackground()
            SettingsScreen.Device -> bindDevice()
            SettingsScreen.Display -> bindDisplay()
            SettingsScreen.Advanced -> bindAdvanced()
            SettingsScreen.Weather -> bindWeather()
            SettingsScreen.System -> bindSystem()
            SettingsScreen.About -> bindAbout()
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
        val teachingStore = WeChatTeachingStore(this)
        val fingerprint = WeChatTeachingFingerprintFactory.capture(this)
        val teachingSnapshot = fingerprint?.let(teachingStore::snapshot)
        val teachingSummary = if (fingerprint == null) {
            getString(R.string.settings_wechat_teaching_wechat_missing)
        } else {
            WeChatTeachingSummaryFormatter.format(this, requireNotNull(teachingSnapshot))
        }
        addRow(
            R.string.settings_wechat_teaching_title,
            teachingSummary,
            R.drawable.ic_settings_action_video,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            action = getString(R.string.settings_wechat_teaching_action_manage)
        ) {
            showScreen(SettingsScreen.WeChatRules)
        }
    }

    private fun bindWechatRules() = with(activity) {
        val store = WeChatTeachingStore(this)
        val record = store.loadRecord()
        val profile = store.load()
        val fingerprint = record?.fingerprint ?: profile?.fingerprint
        val statusSummary = when {
            record?.videoConfirmed == true -> getString(
                R.string.settings_wechat_rules_status_confirmed,
                "${record.fingerprint.manufacturer} ${record.fingerprint.model}",
                record.fingerprint.weChatVersionName
            )
            profile != null -> getString(
                R.string.settings_wechat_rules_status_saved,
                profile.steps.size
            )
            else -> getString(R.string.settings_wechat_rules_status_none)
        }
        addRow(
            if (record == null && profile == null) {
                R.string.settings_wechat_rules_status_title
            } else {
                R.string.settings_wechat_rules_recorded_title
            },
            statusSummary,
            R.drawable.ic_settings_action_video,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            action = getString(
                if (fingerprint == null) {
                    R.string.settings_wechat_teaching_action_prepare
                } else {
                    R.string.settings_wechat_teaching_action_reteach
                }
            )
        ) {
            prepareWechatTeaching()
        }

        val verifiedActions = record?.verifiedActions.orEmpty()
        addRow(
            R.string.settings_wechat_rules_builtin_title,
            if (verifiedActions.isEmpty()) {
                getString(R.string.settings_wechat_rules_builtin_empty)
            } else {
                getString(
                    R.string.settings_wechat_rules_builtin_detail,
                    formatTeachingActions(verifiedActions)
                )
            },
            R.drawable.ic_settings_action_video,
            R.color.launcher_call,
            R.color.launcher_call_soft
        )

        useRowsContainer(R.id.settings_detail_rows_secondary, R.id.settings_detail_card_secondary)
        val learnedSteps = profile?.steps.orEmpty()
            .sortedBy { WeChatTeachingAction.entries.indexOf(it.action) }
        if (learnedSteps.isEmpty()) {
            val verifiedWithoutDifference = record?.videoConfirmed == true && verifiedActions.isNotEmpty()
            addRow(
                if (verifiedWithoutDifference) {
                    R.string.settings_wechat_rules_no_difference_title
                } else {
                    R.string.settings_wechat_rules_learned_empty_title
                },
                if (verifiedWithoutDifference) {
                    getString(
                        R.string.settings_wechat_rules_no_difference_summary,
                        verifiedActions.size
                    )
                } else {
                    getString(R.string.settings_wechat_rules_learned_empty_summary)
                },
                R.drawable.ic_settings_action_video,
                R.color.launcher_system,
                R.color.launcher_system_soft
            )
        } else {
            learnedSteps.forEach { step ->
                addRow(
                    step.action.labelRes(),
                    formatLearnedRule(step, profile!!.reliabilityScore),
                    R.drawable.ic_settings_action_video,
                    R.color.launcher_call,
                    R.color.launcher_call_soft,
                    action = getString(R.string.settings_wechat_rules_delete_action),
                    actionColorRes = R.color.launcher_danger
                ) {
                    showDeleteLearnedRuleDialog(store, profile.fingerprint, step.action)
                }
            }
        }

        if (profile != null || record != null) {
            useRowsContainer(R.id.settings_detail_rows_tertiary, R.id.settings_detail_card_tertiary)
            if (learnedSteps.isNotEmpty()) {
                addRow(
                    R.string.settings_wechat_rules_clear_title,
                    R.string.settings_wechat_rules_clear_summary,
                    R.drawable.ic_settings_action_warning,
                    R.color.launcher_danger,
                    R.color.launcher_danger_soft,
                    action = getString(R.string.settings_wechat_rules_clear_action),
                    actionColorRes = R.color.launcher_danger
                ) {
                    showClearLearnedRulesDialog(store, requireNotNull(profile).fingerprint)
                }
            }
            addRow(
                R.string.settings_wechat_rules_reset_title,
                R.string.settings_wechat_rules_reset_summary,
                R.drawable.ic_settings_action_warning,
                R.color.launcher_danger,
                R.color.launcher_danger_soft,
                action = getString(R.string.settings_wechat_rules_reset_action),
                actionColorRes = R.color.launcher_danger
            ) {
                showResetTeachingDialog(store)
            }
        }
    }

    private fun prepareWechatTeaching() = with(activity) {
        val serviceName = AccessibilityServiceMatcher.componentName(
            packageName,
            SelectToSpeakService::class.java.name
        )
        val accessibilityEnabled = PermissionUtil.isAccessibilityServiceEnabled(this, serviceName)
        val prepareResult = if (accessibilityEnabled) {
            SelectToSpeakService.prepareWeChatTeaching()
        } else {
            WeChatTeachingPrepareResult.SERVICE_NOT_CONNECTED
        }
        val decision = WeChatTeachingEntryPolicy.resolve(accessibilityEnabled, prepareResult)
        Toast.makeText(this, decision.messageRes, Toast.LENGTH_LONG).show()
        if (decision.openAccessibilitySettings) {
            PermissionUtil.openAccessibilitySettings(this)
        }
    }

    private fun formatTeachingActions(actions: Set<WeChatTeachingAction>): String =
        actions.sortedBy { WeChatTeachingAction.entries.indexOf(it) }
            .joinToString("、") { activity.getString(it.labelRes()) }

    private fun formatLearnedRule(step: WeChatTeachingStep, reliabilityScore: Int): String {
        val selector = step.selector
        selector.resourceId?.takeIf(String::isNotBlank)?.let { resourceId ->
            return activity.getString(
                R.string.settings_wechat_rules_selector_id,
                resourceId.substringAfterLast('/'),
                reliabilityScore
            )
        }
        selector.semanticLabel?.let {
            return activity.getString(
                R.string.settings_wechat_rules_selector_text,
                activity.getString(step.action.labelRes()),
                reliabilityScore
            )
        }
        if (selector.centerXRatio != null && selector.centerYRatio != null) {
            return activity.getString(
                R.string.settings_wechat_rules_selector_position,
                (selector.centerXRatio * 100).roundToInt(),
                (selector.centerYRatio * 100).roundToInt(),
                reliabilityScore
            )
        }
        return activity.getString(R.string.settings_wechat_rules_selector_saved, reliabilityScore)
    }

    private fun showDeleteLearnedRuleDialog(
        store: WeChatTeachingStore,
        fingerprint: WeChatTeachingFingerprint,
        action: WeChatTeachingAction
    ) {
        showTeachingDestructiveDialog(
            titleRes = R.string.settings_wechat_rules_delete_title,
            message = activity.getString(
                R.string.settings_wechat_rules_delete_message,
                activity.getString(action.labelRes())
            ),
            confirmRes = R.string.settings_wechat_rules_delete_confirm
        ) {
            if (store.deleteLearnedAction(fingerprint, action)) {
                Toast.makeText(activity, R.string.settings_wechat_rules_delete_done, Toast.LENGTH_SHORT).show()
            }
            bind(SettingsScreen.WeChatRules)
        }
    }

    private fun showClearLearnedRulesDialog(
        store: WeChatTeachingStore,
        fingerprint: WeChatTeachingFingerprint
    ) {
        showTeachingDestructiveDialog(
            titleRes = R.string.settings_wechat_rules_clear_confirm_title,
            message = activity.getString(R.string.settings_wechat_rules_clear_confirm_message),
            confirmRes = R.string.settings_wechat_rules_clear_confirm
        ) {
            if (store.clearLearnedRules(fingerprint)) {
                Toast.makeText(activity, R.string.settings_wechat_rules_clear_done, Toast.LENGTH_SHORT).show()
            }
            bind(SettingsScreen.WeChatRules)
        }
    }

    private fun showResetTeachingDialog(store: WeChatTeachingStore) {
        showTeachingDestructiveDialog(
            titleRes = R.string.settings_wechat_rules_reset_confirm_title,
            message = activity.getString(R.string.settings_wechat_rules_reset_confirm_message),
            confirmRes = R.string.settings_wechat_rules_reset_confirm
        ) {
            if (store.resetAll()) {
                Toast.makeText(activity, R.string.settings_wechat_rules_reset_done, Toast.LENGTH_SHORT).show()
            }
            bind(SettingsScreen.WeChatRules)
        }
    }

    private fun showTeachingDestructiveDialog(
        titleRes: Int,
        message: String,
        confirmRes: Int,
        onConfirm: () -> Unit
    ) {
        val view = activity.layoutInflater.inflate(
            R.layout.dialog_accessibility_prompt,
            FrameLayout(activity),
            false
        )
        view.findViewById<View>(R.id.iv_dialog_vendor_icon).visibility = View.GONE
        view.findViewById<TextView>(R.id.tv_dialog_title).setText(titleRes)
        view.findViewById<TextView>(R.id.tv_dialog_message).text = message
        view.findViewById<TextView>(R.id.tv_cancel_label).setText(R.string.action_cancel)
        view.findViewById<TextView>(R.id.tv_primary_label).setText(confirmRes)
        view.findViewById<MaterialCardView>(R.id.btn_open_settings).setCardBackgroundColor(
            ContextCompat.getColor(activity, R.color.launcher_danger)
        )
        val dialog = LauncherDialogFactory.create(activity, view, dismissOnTouchOutside = false)
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
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

        useRowsContainer(R.id.settings_detail_rows_secondary, R.id.settings_detail_card_secondary)
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
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.toggleChanged(LobsterSetting.AUTO_ANSWER, it)
            )
            bind(SettingsScreen.Calls)
            overviewController.refreshOverviewUi()
        }
        addRow(
            R.string.settings_auto_answer_delay_title,
            getString(R.string.settings_auto_answer_delay_summary, launcherPreferences.getAutoAnswerDelaySeconds()),
            R.drawable.ic_settings_category_calls,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            chevron = true
        ) {
            showValueDialog(
                title = getString(R.string.settings_delay_dialog_title),
                initial = launcherPreferences.getAutoAnswerDelaySeconds(),
                range = 1..30,
                format = { "$it 秒" },
                onSave = { value ->
                    launcherPreferences.setAutoAnswerDelaySeconds(value)
                    LobsterClient.reportUsage(
                        this,
                        LobsterSettingEventFactory.autoAnswerDelayChanged().withTrace(LobsterTrace.newId())
                    )
                }
            )
        }
    }

    private fun bindCallDiagnostics() = with(activity) {
        val vendorPolicy = OemIncomingCallPolicy.forManufacturer(Build.MANUFACTURER)
        addRow(
            R.string.settings_incoming_vendor_title,
            getString(R.string.settings_incoming_vendor_summary, vendorPolicy.vendorName),
            R.drawable.ic_settings_permission_background,
            R.color.launcher_warning,
            R.color.launcher_warning_soft,
            chevron = true
        ) {
            actionController.showIncomingCallVendorDialog(vendorPolicy)
        }

        useRowsContainer(R.id.settings_detail_rows_secondary, R.id.settings_detail_card_secondary)
        val traceDetails = IncomingCallDiagnostics.getDisplayText(this)
        val traceSummary = if (traceDetails == getString(R.string.settings_incoming_trace_empty)) {
            traceDetails
        } else {
            IncomingCallDiagnostics.getNotificationStatusText(this)
        }
        addRow(
            R.string.settings_incoming_trace_title,
            traceSummary,
            R.drawable.ic_settings_action_incoming_guard,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            createListDialog(
                title = getString(R.string.settings_incoming_trace_title),
                message = traceDetails
            ).dialog.show()
        }
    }

    private fun bindSafety() = with(activity) {
        addSwitchRow(
            R.string.settings_fall_detection_title,
            if (launcherPreferences.isFallDetectionEnabled()) {
                R.string.settings_fall_detection_summary_on
            } else {
                R.string.settings_fall_detection_summary_off
            },
            R.drawable.ic_settings_action_warning,
            launcherPreferences.isFallDetectionEnabled()
        ) {
            onFallDetectionToggle(it)
        }
        val contact = launcherPreferences.getFallEmergencyContact()
        addRow(
            R.string.settings_fall_contact_title,
            if (contact.isEmpty()) {
                getString(R.string.settings_fall_contact_summary_empty)
            } else {
                getString(R.string.settings_fall_contact_summary_value, maskPhoneNumber(contact))
            },
            R.drawable.ic_settings_category_contacts,
            R.color.launcher_danger,
            R.color.launcher_danger_soft,
            chevron = true
        ) {
            showFallContactDialog(enableAfterSave = false)
        }
    }

    internal fun onFallDetectionToggle(enabled: Boolean) {
        if (!enabled) {
            activity.launcherPreferences.setFallDetectionEnabled(false)
            LobsterClient.reportUsage(
                activity,
                LobsterSettingEventFactory.toggleChanged(LobsterSetting.FALL_DETECTION, false)
            )
            FallDetectionService.reconcile(activity)
            refreshFallSettingsUi()
            return
        }
        if (activity.launcherPreferences.getFallEmergencyContact().isEmpty()) {
            showFallContactDialog(enableAfterSave = true)
            return
        }
        enableFallDetectionIfReady()
    }

    private fun showFallContactDialog(enableAfterSave: Boolean) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_fall_contact, null, false)
        val input = content.findViewById<EditText>(R.id.fall_contact_input)
        val error = content.findViewById<TextView>(R.id.fall_contact_error)
        input.setText(activity.launcherPreferences.getFallEmergencyContact())
        input.setSelection(input.text.length)
        val dialog = LauncherDialogFactory.create(activity, content, dismissOnTouchOutside = false)
        content.findViewById<View>(R.id.fall_contact_cancel).setOnClickListener {
            dialog.dismiss()
            refreshFallSettingsUi()
        }
        content.findViewById<View>(R.id.fall_contact_save).setOnClickListener {
            val normalized = EmergencyContactNumber.normalize(input.text?.toString())
            if (normalized == null) {
                error.visibility = View.VISIBLE
                input.requestFocus()
                return@setOnClickListener
            }
            activity.launcherPreferences.setFallEmergencyContact(normalized)
            error.visibility = View.GONE
            dialog.dismiss()
            if (enableAfterSave) enableFallDetectionIfReady() else refreshFallSettingsUi()
        }
        dialog.show()
    }

    private fun enableFallDetectionIfReady() {
        val sensorManager = activity.getSystemService(SensorManager::class.java)
        if (sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            Toast.makeText(activity, R.string.settings_fall_sensor_unavailable, Toast.LENGTH_LONG).show()
            refreshFallSettingsUi()
            return
        }
        if (activity.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, R.string.settings_fall_missing_call_permission, Toast.LENGTH_LONG).show()
            activity.requestPhonePermissions()
            refreshFallSettingsUi()
            return
        }
        if (!PermissionUtil.hasNotificationPermission(activity)) {
            Toast.makeText(
                activity,
                R.string.settings_fall_missing_notification_permission,
                Toast.LENGTH_LONG
            ).show()
            activity.requestNotificationPermission()
            refreshFallSettingsUi()
            return
        }
        activity.launcherPreferences.setFallDetectionEnabled(true)
        LobsterClient.reportUsage(
            activity,
            LobsterSettingEventFactory.toggleChanged(LobsterSetting.FALL_DETECTION, true)
        )
        FallDetectionService.reconcile(activity)
        refreshFallSettingsUi()
    }

    private fun refreshFallSettingsUi() {
        if (activity.currentScreen == SettingsScreen.Safety) bind(SettingsScreen.Safety)
        activity.screenController.refreshActive()
    }

    private fun maskPhoneNumber(number: String): String {
        if (number.length <= 7) return number
        return number.take(3) + "****" + number.takeLast(4)
    }

    private fun bindPermissions() = with(activity) {
        bindPermissionEntries(
            listOf(
                PermissionEntry.PhonePermission,
                PermissionEntry.NotificationPermission
            )
        )
        useRowsContainer(R.id.settings_detail_rows_secondary, R.id.settings_detail_card_secondary)
        bindPermissionEntries(
            listOf(
                PermissionEntry.Accessibility,
                PermissionEntry.Overlay
            )
        )
    }

    private fun bindBackground() = with(activity) {
        bindPermissionEntries(
            listOf(
                PermissionEntry.BatteryOptimization,
                PermissionEntry.AutoStart,
                PermissionEntry.BackgroundStart
            )
        )
    }

    private fun SettingsActivity.bindPermissionEntries(entries: List<PermissionEntry>) {
        entries.forEach { entry ->
            val state = overviewController.currentPermissionEntryStates()[entry]
                ?: PermissionEntryState(entry = entry, isReady = false)
            val badge = overviewController.permissionEntryBadge(state)
            val icon = permissionEntryIcon(entry)
            addRow(
                entry.titleRes(),
                overviewController.permissionEntrySummary(state),
                icon.drawableResId,
                icon.tintResId,
                icon.plateResId,
                action = badge.text,
                actionColorRes = badge.textColorResId,
                chevron = true
            ) {
                actionController.openPermissionEntry(entry)
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
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.toggleChanged(LobsterSetting.FULL_CARD_TAP, it)
            )
            bind(SettingsScreen.Device)
        }
    }

    private fun bindDisplay() = with(activity) {
        addRow(
            R.string.settings_icon_scale_title,
            getString(R.string.settings_icon_scale_summary, launcherPreferences.getIconScale()),
            R.drawable.ic_settings_device_scale,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            chevron = true
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
            R.string.settings_font_scale_title,
            fontScaleSummary(),
            R.drawable.ic_settings_permission_accessibility,
            R.color.launcher_ginkgo_deep,
            R.color.launcher_ginkgo_soft,
            chevron = true
        ) {
            showFontScaleDialog()
        }
        addRow(
            R.string.settings_dark_mode_title,
            darkModeSummary(),
            R.drawable.ic_settings_device_display,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            showDarkModeDialog()
        }

        useRowsContainer(R.id.settings_detail_rows_secondary, R.id.settings_detail_card_secondary)
        addRow(
            R.string.settings_advanced_title,
            R.string.settings_advanced_summary,
            R.drawable.ic_settings_permission_battery,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            showScreen(SettingsScreen.Advanced)
        }
    }

    private fun bindAdvanced() = with(activity) {
        addSwitchRow(
            R.string.settings_home_layout_lock_title,
            if (launcherPreferences.isHomeLayoutLocked()) {
                R.string.settings_home_layout_lock_summary_on
            } else {
                R.string.settings_home_layout_lock_summary_off
            },
            R.drawable.ic_settings_category_device,
            launcherPreferences.isHomeLayoutLocked()
        ) {
            launcherPreferences.setHomeLayoutLocked(it)
            bind(SettingsScreen.Advanced)
            overviewController.refreshOverviewUi()
        }
        addRow(
            R.string.settings_home_long_press_title,
            if (launcherPreferences.getHomeLongPressResponse() == LauncherPreferences.HOME_LONG_PRESS_LONG) {
                R.string.settings_home_long_press_summary_long
            } else {
                R.string.settings_home_long_press_summary_standard
            },
            R.drawable.ic_settings_device_scale,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            chevron = true
        ) {
            showHomeLongPressDialog()
        }
        addRow(
            R.string.settings_home_layout_reset_title,
            R.string.settings_home_layout_reset_summary,
            R.drawable.ic_settings_action_update,
            R.color.launcher_danger,
            R.color.launcher_danger_soft,
            chevron = true
        ) {
            showResetHomeLayoutDialog()
        }

        activeRowsContainerId = R.id.settings_detail_rows_secondary
        findViewById<View>(R.id.settings_detail_card_secondary).visibility = View.VISIBLE
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
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.toggleChanged(LobsterSetting.LOW_PERFORMANCE_MODE, it)
            )
            bind(SettingsScreen.Advanced)
            overviewController.refreshOverviewUi()
        }
        addRow(
            R.string.settings_diagnostic_export_title,
            R.string.settings_diagnostic_export_summary,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            exportDiagnosticInfo()
        }
    }

    private fun bindWeather() = with(activity) {
        addRow(
            R.string.settings_weather_city_title,
            getString(R.string.settings_weather_city_summary, weatherPreferences.getCityName()),
            R.drawable.ic_weather_sun,
            R.color.launcher_device,
            R.color.launcher_device_soft,
            chevron = true
        ) {
            showSetCityDialog()
        }
    }

    private fun bindSystem() = with(activity) {
        addRow(
            R.string.settings_system_title,
            R.string.settings_system_summary,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            actionController.openSystemSettings()
        }
        addRow(
            R.string.settings_update_title,
            getString(R.string.settings_update_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            R.drawable.ic_settings_action_update,
            R.color.launcher_call,
            R.color.launcher_call_soft,
            chevron = true
        ) {
            showVersionDetailsDialog()
        }
    }

    private fun bindAbout() = with(activity) {
        findViewById<View>(R.id.settings_about_hero).visibility = View.VISIBLE
        findViewById<View>(R.id.settings_about_tabs).visibility = View.VISIBLE
        findViewById<TextView>(R.id.settings_about_hero_version).text = getString(
            R.string.settings_about_hero_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        findViewById<View>(R.id.settings_about_tab_project).setOnClickListener {
            renderAboutTab(AboutTab.PROJECT)
        }
        findViewById<View>(R.id.settings_about_tab_evidence).setOnClickListener {
            renderAboutTab(AboutTab.EVIDENCE)
        }
        findViewById<View>(R.id.settings_about_tab_service).setOnClickListener {
            renderAboutTab(AboutTab.SERVICE)
        }
        renderAboutTab(activeAboutTab)
    }

    private fun renderAboutTab(tab: AboutTab) = with(activity) {
        activeAboutTab = tab
        findViewById<View>(R.id.settings_about_tab_project).isSelected = tab == AboutTab.PROJECT
        findViewById<View>(R.id.settings_about_tab_evidence).isSelected = tab == AboutTab.EVIDENCE
        findViewById<View>(R.id.settings_about_tab_service).isSelected = tab == AboutTab.SERVICE

        findViewById<LinearLayout>(R.id.settings_detail_rows).removeAllViews()
        findViewById<LinearLayout>(R.id.settings_detail_rows_secondary).removeAllViews()
        findViewById<LinearLayout>(R.id.settings_detail_rows_tertiary).removeAllViews()
        findViewById<View>(R.id.settings_detail_card_secondary).visibility = View.GONE
        findViewById<View>(R.id.settings_detail_card_tertiary).visibility = View.GONE
        activeRowsContainerId = R.id.settings_detail_rows

        when (tab) {
            AboutTab.PROJECT -> bindAboutProject()
            AboutTab.EVIDENCE -> bindAboutEvidence()
            AboutTab.SERVICE -> bindAboutService()
        }
    }

    private fun SettingsActivity.bindAboutProject() {
        addRow(
            R.string.settings_about_source_title,
            R.string.settings_about_source_summary,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            openAboutUri(ABOUT_SOURCE_URL)
        }.compactAboutRow(hideSummary = true)
        addRow(
            R.string.settings_about_contact_title,
            R.string.settings_about_contact_summary,
            R.drawable.ic_settings_category_contacts,
            R.color.launcher_contacts,
            R.color.launcher_contacts_soft,
            chevron = true
        ) {
            openAboutUri(ABOUT_CONTACT_URI)
        }.compactAboutRow()
        addRow(
            R.string.settings_user_report_title,
            R.string.settings_user_report_summary,
            R.drawable.ic_settings_action_warning,
            R.color.launcher_warning,
            R.color.launcher_warning_soft,
            chevron = true
        ) {
            showUserReportDialog()
        }.compactAboutRow(hideSummary = true)
    }

    private fun SettingsActivity.bindAboutEvidence() {
        addAboutView(R.layout.view_about_evidence_fall)
        useRowsContainer(R.id.settings_detail_rows_secondary, R.id.settings_detail_card_secondary)
        addAboutView(R.layout.view_about_evidence_design)
        useRowsContainer(R.id.settings_detail_rows_tertiary, R.id.settings_detail_card_tertiary)
        addAboutView(R.layout.view_about_evidence_other)
    }

    private fun SettingsActivity.bindAboutService() {
        addRow(
            R.string.settings_about_usage_title,
            R.string.settings_about_usage_summary,
            R.drawable.ic_settings_action_warning,
            R.color.launcher_danger,
            R.color.launcher_danger_soft,
            chevron = true
        ) {
            showAboutMessage(R.string.settings_about_usage_title, R.string.settings_about_usage_message)
        }.compactAboutRow(hideSummary = true)
        addRow(
            R.string.settings_about_privacy_title,
            R.string.settings_about_privacy_summary,
            R.drawable.ic_settings_category_permissions,
            R.color.launcher_warning,
            R.color.launcher_warning_soft,
            chevron = true
        ) {
            startActivity(LegalDocumentActivity.createIntent(this, LegalDocument.PRIVACY))
        }.compactAboutRow(hideSummary = true)
        addRow(
            R.string.settings_about_terms_title,
            R.string.settings_about_terms_summary,
            R.drawable.ic_settings_category_system,
            R.color.launcher_system,
            R.color.launcher_system_soft,
            chevron = true
        ) {
            startActivity(LegalDocumentActivity.createIntent(this, LegalDocument.TERMS))
        }.compactAboutRow(hideSummary = true)
        addRow(
            R.string.settings_about_license_title,
            R.string.settings_about_license_summary,
            R.drawable.ic_settings_permission_accessibility,
            R.color.launcher_ginkgo_deep,
            R.color.launcher_ginkgo_soft,
            chevron = true
        ) {
            showAboutMessage(R.string.settings_about_license_title, R.string.settings_about_license_message)
        }.compactAboutRow(hideSummary = true)
    }

    private fun View.compactAboutRow(hideSummary: Boolean = false) {
        findViewById<View>(R.id.detail_row_click_target).minimumHeight =
            (56 * resources.displayMetrics.density).toInt()
        findViewById<TextView>(R.id.detail_row_title)
            .setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        if (hideSummary) {
            findViewById<View>(R.id.detail_row_summary).visibility = View.GONE
        }
    }

    private fun SettingsActivity.addAboutView(layoutRes: Int) {
        val container = findViewById<LinearLayout>(activeRowsContainerId)
        container.addView(layoutInflater.inflate(layoutRes, container, false))
    }

    private fun openAboutUri(value: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
        if (runCatching { activity.startActivity(intent) }.isFailure) {
            Toast.makeText(activity, R.string.settings_about_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutMessage(titleRes: Int, messageRes: Int) {
        activity.createListDialog(
            title = activity.getString(titleRes),
            message = activity.getString(messageRes)
        ).dialog.show()
    }

    internal fun showUserReportDialog(): AlertDialog {
        val content = activity.layoutInflater.inflate(R.layout.dialog_user_report, null, false)
        val types = LobsterUserReportType.entries
        var selectedType = types.first()
        val typeField = content.findViewById<TextView>(R.id.user_report_type).apply {
            text = selectedType.label
            setOnClickListener {
                val choice = activity.createListDialog(
                    title = activity.getString(R.string.settings_user_report_type),
                    message = activity.getString(R.string.settings_user_report_type_hint)
                )
                types.forEach { type ->
                    val isSelected = type == selectedType
                    activity.addDialogEntry(
                        context = choice,
                        title = type.label,
                        summary = if (isSelected) {
                            activity.getString(R.string.settings_user_report_type_selected)
                        } else {
                            activity.getString(R.string.settings_user_report_type_select)
                        },
                        badge = activity.actionBadge(
                            activity.getString(
                                if (isSelected) R.string.settings_user_report_type_selected
                                else R.string.settings_user_report_type_select
                            )
                        ),
                        compact = true
                    ) {
                        selectedType = type
                        text = type.label
                        choice.dialog.dismiss()
                    }
                }
                choice.dialog.show()
            }
        }
        val description = content.findViewById<EditText>(R.id.user_report_description)
        val reproductionSteps = content.findViewById<EditText>(R.id.user_report_steps)
        val error = content.findViewById<TextView>(R.id.user_report_error)
        val privacyDetails = content.findViewById<TextView>(R.id.user_report_privacy_details)
        val privacyToggle = content.findViewById<TextView>(R.id.user_report_privacy_toggle)
        val dialog = LauncherDialogFactory.create(activity, content, dismissOnTouchOutside = false)
        privacyToggle.setOnClickListener {
            val showDetails = privacyDetails.visibility != View.VISIBLE
            privacyDetails.visibility = if (showDetails) View.VISIBLE else View.GONE
            privacyToggle.setText(
                if (showDetails) R.string.settings_user_report_privacy_hide
                else R.string.settings_user_report_privacy_show
            )
        }
        content.findViewById<View>(R.id.user_report_cancel).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.user_report_submit).setOnClickListener {
            val event = LobsterUserReportFactory.create(
                type = selectedType,
                description = description.text?.toString().orEmpty(),
                reproductionSteps = reproductionSteps.text?.toString()
            )
            if (event == null) {
                error.visibility = View.VISIBLE
                description.requestFocus()
                return@setOnClickListener
            }
            error.visibility = View.GONE
            LobsterClient.reportUsage(activity, event)
            dialog.dismiss()
            Toast.makeText(activity, R.string.settings_user_report_queued, Toast.LENGTH_LONG).show()
        }
        dialog.show()
        return dialog
    }

    private fun SettingsActivity.useRowsContainer(containerId: Int, cardId: Int) {
        activeRowsContainerId = containerId
        findViewById<View>(cardId).visibility = View.VISIBLE
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
        val container = findViewById<LinearLayout>(activeRowsContainerId)
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
        val dialog = LauncherDialogFactory.create(activity, view)
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

    private fun fontScaleSummary(): String = with(activity) {
        getString(
            when (launcherPreferences.getFontScaleMode()) {
                LauncherPreferences.FONT_SCALE_STANDARD -> R.string.settings_font_scale_summary_standard
                LauncherPreferences.FONT_SCALE_LARGE -> R.string.settings_font_scale_summary_large
                LauncherPreferences.FONT_SCALE_EXTRA_LARGE -> R.string.settings_font_scale_summary_extra_large
                else -> R.string.settings_font_scale_summary_system
            }
        )
    }

    internal fun showFontScaleDialog(): android.app.Dialog {
        val currentMode = activity.launcherPreferences.getFontScaleMode()
        val options = listOf(
            LauncherPreferences.FONT_SCALE_SYSTEM to R.string.settings_font_scale_system_short,
            LauncherPreferences.FONT_SCALE_STANDARD to R.string.settings_font_scale_summary_standard,
            LauncherPreferences.FONT_SCALE_LARGE to R.string.settings_font_scale_summary_large,
            LauncherPreferences.FONT_SCALE_EXTRA_LARGE to R.string.settings_font_scale_summary_extra_large
        )
        val dialog = activity.createChoiceDialog(
            title = activity.getString(R.string.settings_font_scale_title),
            message = activity.getString(R.string.settings_font_scale_dialog_message)
        )
        options.forEach { (value, titleRes) ->
            val isCurrent = value == currentMode
            activity.addDialogChoice(
                context = dialog,
                title = activity.getString(titleRes),
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
                dialog.dialog.dismiss()
                if (value != currentMode) {
                    activity.launcherPreferences.setFontScaleMode(value)
                    activity.intent.putExtra(SettingsActivity.EXTRA_SECTION, SettingsScreen.Display.key)
                    activity.recreate()
                }
            }
        }
        dialog.dialog.show()
        return dialog.dialog
    }

    internal fun showDarkModeDialog(): android.app.Dialog {
        val currentMode = activity.launcherPreferences.getDarkMode()
        val options = listOf(
            Pair(
                LauncherPreferences.DARK_MODE_SYSTEM,
                R.string.settings_dark_mode_system_short
            ),
            Pair(
                LauncherPreferences.DARK_MODE_LIGHT,
                R.string.settings_dark_mode_light
            ),
            Pair(
                LauncherPreferences.DARK_MODE_DARK,
                R.string.settings_dark_mode_dark
            )
        )
        val dialog = activity.createChoiceDialog(
            title = activity.getString(R.string.settings_dark_mode_title),
            message = activity.getString(R.string.settings_dark_mode_dialog_message)
        )
        options.forEach { (value, titleRes) ->
            val isCurrent = value == currentMode
            activity.addDialogChoice(
                context = dialog,
                title = activity.getString(titleRes),
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
                LobsterClient.reportUsage(
                    activity,
                    LobsterSettingEventFactory.darkModeChanged().withTrace(LobsterTrace.newId())
                )
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
        SettingsScreen.WeChatRules -> R.string.settings_wechat_rules_summary
        SettingsScreen.Calls -> R.string.settings_calls_summary
        SettingsScreen.CallDiagnostics -> R.string.settings_diagnostics_summary
        SettingsScreen.Safety -> R.string.settings_safety_summary
        SettingsScreen.Permissions -> R.string.settings_permissions_summary
        SettingsScreen.Background -> R.string.settings_background_summary
        SettingsScreen.Device -> R.string.settings_device_summary
        SettingsScreen.Display -> R.string.settings_display_summary
        SettingsScreen.Advanced -> R.string.settings_advanced_summary
        SettingsScreen.Weather -> R.string.settings_weather_summary_short
        SettingsScreen.System -> R.string.settings_system_summary_short
        SettingsScreen.About -> R.string.settings_about_subtitle
        SettingsScreen.StandardOverview -> R.string.settings_subtitle
        SettingsScreen.ElderOverview -> R.string.settings_elder_subtitle
    }

    private fun PermissionEntry.titleRes(): Int = when (this) {
        PermissionEntry.DefaultPhone -> R.string.settings_default_phone_title
        PermissionEntry.PhonePermission -> R.string.settings_phone_permission_title
        PermissionEntry.NotificationPermission -> R.string.settings_notification_permission_title
        PermissionEntry.DefaultLauncher -> R.string.set_default_launcher_title
        PermissionEntry.BatteryOptimization -> R.string.settings_battery_title
        PermissionEntry.AutoStart -> R.string.settings_autostart_title
        PermissionEntry.BackgroundStart -> R.string.settings_bg_start_title
        PermissionEntry.Accessibility -> R.string.settings_accessibility_title
        PermissionEntry.Overlay -> R.string.settings_overlay_title
    }
}

private const val ABOUT_SOURCE_URL = "https://github.com/bjfwan/yinxing"
private const val ABOUT_CONTACT_URI = "mailto:2632507193@qq.com"
