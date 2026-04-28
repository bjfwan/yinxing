package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.feature.incoming.IncomingGuardItem
import com.yinxing.launcher.feature.incoming.IncomingGuardReadinessEvaluator
import com.yinxing.launcher.feature.phone.PhoneContactManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SettingsOverviewController(
    private val activity: SettingsActivity
) {
    fun bindViews() = activity.bindOverviewViews()

    fun bindActions(
        onBack: () -> Unit,
        onShowIncomingGuardSheet: () -> Unit,
        onShowContactsSheet: () -> Unit,
        onShowAutoAnswerSheet: () -> Unit,
        onShowPermissionGroupsSheet: () -> Unit,
        onShowDeviceSettingsSheet: () -> Unit,
        onShowSystemSheet: () -> Unit
    ) {
        with(activity) {
            findViewById<View>(R.id.btn_back).setOnClickListener { onBack() }
            findViewById<View>(R.id.btn_card_incoming_guard).setOnClickListener { onShowIncomingGuardSheet() }
            btnIncomingGuardAction.setOnClickListener { onShowIncomingGuardSheet() }
            findViewById<View>(R.id.btn_card_contacts).setOnClickListener { onShowContactsSheet() }
            findViewById<View>(R.id.btn_card_auto_answer).setOnClickListener { onShowAutoAnswerSheet() }
            findViewById<View>(R.id.btn_card_permissions).setOnClickListener { onShowPermissionGroupsSheet() }
            findViewById<View>(R.id.btn_card_device).setOnClickListener { onShowDeviceSettingsSheet() }
            findViewById<View>(R.id.btn_card_system).setOnClickListener { onShowSystemSheet() }
        }
    }

    fun refreshOverviewUi() = activity.refreshOverviewUi()

    fun performOverviewRefresh() = activity.performOverviewRefresh()

    fun updateAutoAnswerHubCard() = activity.updateAutoAnswerHubCard()

    fun updateSystemHubCard() = activity.updateSystemHubCard()

    fun refreshDeviceHubCard() = activity.refreshDeviceHubCard()

    suspend fun loadContactCounts(): ContactCounts = activity.loadContactCounts()

    fun currentIncomingGuardReadiness() = activity.incomingGuardReadiness

    fun currentPermissionEntryStates() = activity.permissionEntryStates

    fun permissionGroupRenderState(group: PermissionGroup): GroupRenderState {
        return activity.permissionGroupRenderState(group)
    }

    fun permissionEntrySummary(state: PermissionEntryState): String {
        return activity.permissionEntrySummary(state)
    }

    fun permissionEntryTitle(entry: PermissionEntry): String {
        return activity.permissionEntryTitle(entry)
    }

    fun permissionEntryBadge(state: PermissionEntryState): BadgeStyle {
        return activity.permissionEntryBadge(state)
    }

    fun guardTitle(item: IncomingGuardItem): String {
        return activity.guardTitle(item)
    }

    fun applyInfoBadge(tv: TextView, text: String, textColorResId: Int, backgroundColorResId: Int) {
        activity.applyInfoBadge(tv, text, textColorResId, backgroundColorResId)
    }

    fun onDestroy() {
        activity.mainHandler.removeCallbacks(activity.overviewRefreshRunnable)
        activity.contactsSummaryJob?.cancel()
    }
}

internal fun SettingsActivity.bindOverviewViews() {
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

internal fun SettingsActivity.bindOverviewActions() {
    findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    findViewById<View>(R.id.btn_card_incoming_guard).setOnClickListener { showIncomingGuardSheet() }
    btnIncomingGuardAction.setOnClickListener { showIncomingGuardSheet() }
    findViewById<View>(R.id.btn_card_contacts).setOnClickListener { showContactsSheet() }
    findViewById<View>(R.id.btn_card_auto_answer).setOnClickListener { showAutoAnswerSheet() }
    findViewById<View>(R.id.btn_card_permissions).setOnClickListener { showPermissionGroupsSheet() }
    findViewById<View>(R.id.btn_card_device).setOnClickListener { showDeviceSettingsSheet() }
    findViewById<View>(R.id.btn_card_system).setOnClickListener { showSystemSheet() }
}

internal fun SettingsActivity.refreshOverviewUi() {
    if (overviewRefreshQueued) {
        return
    }
    overviewRefreshQueued = true
    mainHandler.post(overviewRefreshRunnable)
}

internal fun SettingsActivity.performOverviewRefresh() {
    updateContactsHubSummary()
    updateAutoAnswerHubCard()
    updateSystemHubCard()
    refreshAllPermissionUi()
}

internal fun SettingsActivity.updateContactsHubSummary() {
    val homeAppCount = launcherPreferences.getSelectedPackages().size
    contactsSummaryJob?.cancel()
    contactsSummaryJob = lifecycleScope.launch {
        try {
            val counts = loadContactCounts()
            tvContactsHubSummary.text = getString(
                R.string.settings_contacts_hub_summary,
                counts.phoneCount,
                counts.videoCount,
                homeAppCount
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            tvContactsHubSummary.text = getString(
                R.string.settings_contacts_hub_summary,
                0,
                0,
                homeAppCount
            )
        }
    }
}

internal fun SettingsActivity.updateAutoAnswerHubCard() {
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

internal fun SettingsActivity.updateSystemHubCard() {
    tvSystemHubSummary.text = getString(
        R.string.settings_weather_city_summary,
        weatherPreferences.getCityName()
    )
}

internal fun SettingsActivity.refreshPermissionAndDeviceUi() {
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

internal fun SettingsActivity.refreshAllPermissionUi() {
    refreshPermissionAndDeviceUi()
}

internal suspend fun SettingsActivity.loadContactCounts(): ContactCounts {
    return withContext(Dispatchers.IO) {
        ContactCounts(
            phoneCount = PhoneContactManager.getInstance(this@loadContactCounts).getContactCount(),
            videoCount = ContactManager.getInstance(this@loadContactCounts).getContactCount()
        )
    }
}

internal fun SettingsActivity.refreshIncomingGuardUi() {
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

internal fun SettingsActivity.buildPermissionEntryStates(
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

internal fun SettingsActivity.refreshPermissionHubCard() {
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

internal fun SettingsActivity.refreshDeviceHubCard() {
    val isDefault = isDefaultLauncher()
    val defaultSummary = if (isDefault) {
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
        text = if (isDefault) {
            getString(R.string.settings_guard_status_done)
        } else {
            getString(R.string.settings_guard_status_pending)
        },
        textColorResId = if (isDefault) R.color.launcher_action_dark else R.color.launcher_warning,
        backgroundColorResId = if (isDefault) R.color.launcher_primary_soft else R.color.launcher_warning_soft
    )
}

internal fun SettingsActivity.permissionGroupRenderState(group: PermissionGroup): GroupRenderState {
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

internal fun SettingsActivity.permissionEntrySummary(state: PermissionEntryState): String {
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

internal fun SettingsActivity.permissionEntryTitle(entry: PermissionEntry): String {
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

internal fun SettingsActivity.permissionEntryBadge(state: PermissionEntryState): BadgeStyle {
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

internal fun SettingsActivity.guardTitle(item: com.yinxing.launcher.feature.incoming.IncomingGuardItem): String {
    return permissionEntryTitle(item.toPermissionEntry())
}

internal fun SettingsActivity.applyInfoBadge(
    tv: TextView,
    text: String,
    textColorResId: Int,
    backgroundColorResId: Int
) {
    tv.text = text
    tv.setTextColor(getColor(textColorResId))
    tv.setBackgroundResource(R.drawable.edit_text_background)
    tv.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(backgroundColorResId))
}
