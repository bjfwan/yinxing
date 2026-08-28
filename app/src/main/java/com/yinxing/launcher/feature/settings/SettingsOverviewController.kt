package com.yinxing.launcher.feature.settings

import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.feature.incoming.DefaultPhoneRoleController
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
    fun bindActions(
        onBack: () -> Unit,
        onShowFamilySetup: () -> Unit,
        onShowIncomingGuard: () -> Unit,
        onShowContacts: () -> Unit,
        onShowCalls: () -> Unit,
        onShowDiagnostics: () -> Unit,
        onShowSafety: () -> Unit,
        onShowPermissions: () -> Unit,
        onShowBackground: () -> Unit,
        onShowDevice: () -> Unit,
        onShowDisplay: () -> Unit,
        onShowWeather: () -> Unit,
        onShowSystem: () -> Unit,
        onShowAbout: () -> Unit
    ) {
        with(activity) {
            findViewById<View>(R.id.btn_back).setOnClickListener { onBack() }
            findViewById<View>(R.id.btn_family_setup).setOnClickListener { onShowFamilySetup() }
            findViewById<View>(R.id.btn_card_incoming_guard).setOnClickListener { onShowIncomingGuard() }
            btnIncomingGuardAction.setOnClickListener { onShowIncomingGuard() }
            findViewById<View>(R.id.btn_detail_contacts).setOnClickListener { onShowContacts() }
            findViewById<View>(R.id.btn_detail_calls).setOnClickListener { onShowCalls() }
            findViewById<View>(R.id.btn_detail_diagnostics).setOnClickListener { onShowDiagnostics() }
            findViewById<View>(R.id.btn_detail_safety).setOnClickListener { onShowSafety() }
            findViewById<View>(R.id.btn_detail_permissions).setOnClickListener { onShowPermissions() }
            findViewById<View>(R.id.btn_detail_background).setOnClickListener { onShowBackground() }
            findViewById<View>(R.id.btn_detail_device).setOnClickListener { onShowDevice() }
            findViewById<View>(R.id.btn_detail_display).setOnClickListener { onShowDisplay() }
            findViewById<View>(R.id.btn_detail_weather).setOnClickListener { onShowWeather() }
            findViewById<View>(R.id.btn_detail_system).setOnClickListener { onShowSystem() }
            findViewById<View>(R.id.btn_detail_about).setOnClickListener { onShowAbout() }
        }
    }

    fun refreshOverviewUi() = activity.refreshOverviewUi()

    fun performOverviewRefresh() = activity.performOverviewRefresh()

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
        activity.contactsSummaryJob?.cancel()
    }
}

internal fun SettingsActivity.refreshOverviewUi() {
    postOverviewRefresh()
}

internal fun SettingsActivity.performOverviewRefresh() {
    updateContactsHubSummary()
    updateSystemHubCard()
    refreshAllPermissionUi()
}

internal fun SettingsActivity.updateContactsHubSummary() {
    contactsSummaryJob?.cancel()
    contactsSummaryJob = lifecycleScope.launch {
        try {
            val counts = loadContactCounts()
            navigationSummary(R.id.btn_detail_contacts).text = getString(
                R.string.settings_contacts_hub_summary,
                counts.phoneCount,
                counts.videoCount
            )
            navigationValue(R.id.btn_detail_contacts).text = getString(
                R.string.settings_contacts_hub_value,
                counts.phoneCount,
                counts.videoCount
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            navigationSummary(R.id.btn_detail_contacts).text = getString(
                R.string.settings_contacts_hub_summary,
                0,
                0
            )
            navigationValue(R.id.btn_detail_contacts).text = getString(
                R.string.settings_contacts_hub_value,
                0,
                0
            )
        }
    }
}

internal fun SettingsActivity.updateSystemHubCard() {
    navigationSummary(R.id.btn_detail_weather).text = getString(
        R.string.settings_weather_city_summary,
        weatherPreferences.getCityName()
    )
    navigationValue(R.id.btn_detail_weather).text = weatherPreferences.getCityName()
    navigationValue(R.id.btn_detail_calls).setText(
        if (launcherPreferences.isAutoAnswerEnabled()) R.string.settings_state_on
        else R.string.settings_state_off
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
        isDefaultPhone = DefaultPhoneRoleController.isHeld(this),
        hasNotificationPermission = PermissionUtil.hasNotificationPermission(this),
        ignoresBatteryOptimizations = PermissionUtil.isIgnoringBatteryOptimizations(this),
        autoStartConfirmed = launcherPreferences.isAutoStartConfirmed(),
        backgroundStartConfirmed = launcherPreferences.isBackgroundStartConfirmed()
    )
    val blocker = incomingGuardReadiness.blocker?.item

    if (incomingGuardReadiness.isReady) {
        tvIncomingGuardProgress.text = getString(R.string.settings_incoming_guard_status_ready)
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
        tvIncomingGuardProgress.text = getString(
            R.string.settings_incoming_guard_pending_item,
            blockerTitle
        )
        applyInfoBadge(
            tv = tvIncomingGuardStatus,
            text = getString(R.string.settings_incoming_guard_status_pending),
            textColorResId = R.color.launcher_warning,
            backgroundColorResId = R.color.launcher_warning_soft
        )
        tvIncomingGuardSummary.text = if (blocker == IncomingGuardItem.PhonePermission) {
            getString(R.string.settings_incoming_guard_phone_risk)
        } else {
            getString(R.string.settings_incoming_guard_summary_blocked, blockerTitle)
        }
        tvIncomingGuardAction.text = getString(R.string.settings_incoming_guard_action_now)
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
    states[PermissionEntry.DefaultLauncher] = PermissionEntryState(
        entry = PermissionEntry.DefaultLauncher,
        isReady = isDefaultLauncher()
    )
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
    val permissionEntries = listOf(
        PermissionEntry.PhonePermission,
        PermissionEntry.NotificationPermission,
        PermissionEntry.Accessibility,
        PermissionEntry.Overlay
    )
    val states = permissionEntries.mapNotNull(permissionEntryStates::get)
    val blocker = states.firstOrNull { !it.isReady }
    navigationSummary(R.id.btn_detail_permissions).text = if (blocker == null) {
        getString(R.string.settings_permissions_hub_summary_ready)
    } else {
        getString(
            R.string.settings_permissions_hub_summary_pending,
            permissionEntryTitle(blocker.entry)
        )
    }
    navigationValue(R.id.btn_detail_permissions).text = if (blocker == null) {
        getString(R.string.settings_guard_status_done)
    } else {
        getString(R.string.settings_pending_count_value, states.count { !it.isReady })
    }

    val backgroundEntries = listOf(
        PermissionEntry.BatteryOptimization,
        PermissionEntry.AutoStart,
        PermissionEntry.BackgroundStart
    )
    val backgroundStates = backgroundEntries.mapNotNull(permissionEntryStates::get)
    navigationValue(R.id.btn_detail_background).text = if (backgroundStates.all { it.isReady }) {
        getString(R.string.settings_guard_status_done)
    } else {
        getString(R.string.settings_pending_count_value, backgroundStates.count { !it.isReady })
    }
}

internal fun SettingsActivity.refreshDeviceHubCard() {
    val isDefault = isDefaultLauncher()
    val defaultSummary = if (isDefault) {
        getString(R.string.settings_device_hub_default_ready)
    } else {
        getString(R.string.settings_device_hub_default_pending)
    }
    val performanceSummary = if (launcherPreferences.isLowPerformanceModeEnabled()) {
        getString(R.string.settings_device_hub_low_performance_on)
    } else {
        getString(R.string.settings_device_hub_low_performance_off)
    }
    navigationSummary(R.id.btn_detail_device).text = getString(
        R.string.settings_device_hub_summary,
        defaultSummary,
        performanceSummary
    )
    navigationValue(R.id.btn_detail_device).text = defaultSummary
}

private fun SettingsActivity.navigationSummary(rootId: Int): TextView {
    return findViewById<View>(rootId).findViewById(R.id.navigation_summary)
}

private fun SettingsActivity.navigationValue(rootId: Int): TextView {
    return findViewById<View>(rootId).findViewById(R.id.navigation_value)
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
        PermissionEntry.DefaultPhone -> getString(
            if (state.isReady) R.string.settings_default_phone_summary_on
            else R.string.settings_default_phone_summary_off
        )
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
