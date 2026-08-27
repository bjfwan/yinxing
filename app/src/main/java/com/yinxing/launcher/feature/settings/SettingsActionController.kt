package com.yinxing.launcher.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.view.updateLayoutParams
import com.yinxing.launcher.R
import com.yinxing.launcher.common.util.DebugLog
import com.yinxing.launcher.common.util.OemLauncherIconLoader
import com.yinxing.launcher.common.util.OemLauncherPolicy
import com.yinxing.launcher.common.util.OemLauncherProfile
import com.yinxing.launcher.common.util.OemLauncherSupport
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.feature.incoming.DefaultPhoneRoleController
import com.yinxing.launcher.feature.incoming.IncomingGuardItem
import com.yinxing.launcher.feature.incoming.OemIncomingCallPolicy

private const val TAG = "SettingsActionController"

internal class SettingsActionController(
    private val activity: SettingsActivity
) {
    fun onPhonePermissionResult(results: Map<String, Boolean>) {
        val granted = results.values.all { it }
        if (granted) {
            Toast.makeText(
                activity,
                activity.getString(R.string.settings_phone_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            PermissionUtil.openAppDetailSettings(activity)
        }
        activity.overviewController.refreshOverviewUi()
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            Toast.makeText(
                activity,
                activity.getString(R.string.settings_notification_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            PermissionUtil.openNotificationSettings(activity)
        }
        activity.overviewController.refreshOverviewUi()
        continueDefaultPhoneRoleIfReady()
    }

    fun onDefaultPhoneRoleResult() {
        activity.overviewController.refreshOverviewUi()
        if (DefaultPhoneRoleController.isHeld(activity)) {
            activity.requestDefaultPhoneRole()
        } else {
            Toast.makeText(
                activity,
                activity.getString(R.string.settings_default_phone_required_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openPermissionEntry(entry: PermissionEntry) = activity.openPermissionEntry(entry)

    fun openIncomingGuardItem(item: IncomingGuardItem) = activity.openIncomingGuardItem(item)

    fun openSystemSettings() = activity.openSystemSettings()

    fun showSetDefaultLauncherDialog() = activity.showSetDefaultLauncherDialog()

    fun clearDefaultLauncher() = activity.clearDefaultLauncher()

    fun isDefaultLauncher(): Boolean = activity.isDefaultLauncher()

    fun requestDefaultPhoneRole() = activity.requestDefaultPhoneRole()

    fun showIncomingCallVendorDialog(policy: OemIncomingCallPolicy) =
        activity.showIncomingCallVendorDialog(policy)

    fun continueDefaultPhoneRoleIfReady() {
        if (!activity.runtime.continueDefaultPhoneAfterNotification ||
            !PermissionUtil.hasNotificationPermission(activity)
        ) return
        activity.runtime.continueDefaultPhoneAfterNotification = false
        activity.requestDefaultPhoneRole()
    }
}

internal fun SettingsActivity.openPermissionEntry(entry: PermissionEntry) {
    when (entry) {
        PermissionEntry.DefaultPhone -> requestDefaultPhoneRole()
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

internal fun SettingsActivity.openIncomingGuardItem(item: IncomingGuardItem) {
    when (item) {
        IncomingGuardItem.DefaultPhone -> requestDefaultPhoneRole()
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

internal fun SettingsActivity.requestPhonePermissions() {
    if (!DefaultPhoneRoleController.isHeld(this)) {
        requestDefaultPhoneRole()
        return
    }
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

internal fun SettingsActivity.requestDefaultPhoneRole() {
    if (DefaultPhoneRoleController.isHeld(this)) {
        if (!PermissionUtil.hasNotificationPermission(this)) {
            runtime.continueDefaultPhoneAfterNotification = true
            requestNotificationPermission()
            return
        }
        if (!PermissionUtil.hasPhonePermission(this)) {
            requestPhonePermissions()
            return
        }
        Toast.makeText(this, R.string.settings_default_phone_summary_on, Toast.LENGTH_SHORT).show()
        return
    }
    if (!PermissionUtil.hasNotificationPermission(this)) {
        runtime.continueDefaultPhoneAfterNotification = true
        requestNotificationPermission()
        return
    }
    val requestIntent = DefaultPhoneRoleController.createRequestIntent(this)
    if (requestIntent != null) {
        runCatching { defaultPhoneRoleLauncher.launch(requestIntent) }
            .onFailure { openDefaultPhoneSettings() }
    } else {
        openDefaultPhoneSettings()
    }
}

private fun SettingsActivity.openDefaultPhoneSettings() {
    val candidates = listOf(
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
        },
        Intent(Settings.ACTION_SETTINGS)
    )
    for (candidate in candidates) {
        if (runCatching { startActivity(candidate); true }.getOrDefault(false)) return
    }
    Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_SHORT).show()
}

internal fun SettingsActivity.showIncomingCallVendorDialog(
    policy: OemIncomingCallPolicy
): AlertDialog {
    val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
    dialogView.findViewById<TextView>(R.id.tv_dialog_title).text =
        getString(R.string.settings_incoming_vendor_dialog_title, policy.vendorName)
    dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = getString(policy.messageRes())
    dialogView.findViewById<TextView>(R.id.tv_cancel_label).setText(R.string.action_cancel)
    dialogView.findViewById<TextView>(R.id.tv_primary_label).text = getString(
        if (policy.requiresDefaultPhoneRole && !DefaultPhoneRoleController.isHeld(this)) {
            R.string.settings_default_phone_action
        } else {
            R.string.action_go_to_settings
        }
    )
    val dialog = AlertDialog.Builder(this).setView(dialogView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
    dialogView.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
        dialog.dismiss()
        if (policy.requiresDefaultPhoneRole && !DefaultPhoneRoleController.isHeld(this)) {
            requestDefaultPhoneRole()
        } else {
            PermissionUtil.openAutoStartSettings(this)
        }
    }
    dialog.show()
    return dialog
}

private fun OemIncomingCallPolicy.messageRes(): Int = when (vendorKey) {
    "xiaomi" -> R.string.settings_incoming_vendor_xiaomi
    "vivo" -> R.string.settings_incoming_vendor_vivo
    "huawei" -> R.string.settings_incoming_vendor_huawei
    "honor" -> R.string.settings_incoming_vendor_honor
    "oplus" -> R.string.settings_incoming_vendor_oplus
    "samsung" -> R.string.settings_incoming_vendor_samsung
    "meizu" -> R.string.settings_incoming_vendor_meizu
    else -> R.string.settings_incoming_vendor_android
}

internal fun SettingsActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !PermissionUtil.hasNotificationPermission(this)
    ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        PermissionUtil.openNotificationSettings(this)
    }
}

internal fun SettingsActivity.showManualCheckDialog(item: IncomingGuardItem) {
    val title = overviewController.guardTitle(item)
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

internal fun SettingsActivity.setManualGuardConfirmation(item: IncomingGuardItem, confirmed: Boolean) {
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
            overviewController.guardTitle(item)
        ),
        Toast.LENGTH_SHORT
    ).show()
    overviewController.refreshOverviewUi()
}

internal fun SettingsActivity.openSystemSettings() {
    try {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
    }
}

internal fun SettingsActivity.isDefaultLauncher(): Boolean {
    return PermissionUtil.isDefaultLauncher(this)
}

internal fun SettingsActivity.showSetDefaultLauncherDialog(): AlertDialog {
    val profile = OemLauncherPolicy.profile(Build.MANUFACTURER)
    val secondarySettingsAction = profile.secondarySettingsAction
    val hasSecondaryStep = secondarySettingsAction != null
    val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_prompt, null)
    dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = getString(R.string.set_default_launcher_title)
    dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = getString(profile.messageRes())
    dialogView.findViewById<TextView>(R.id.tv_cancel_label).text = getString(
        if (hasSecondaryStep) R.string.set_default_launcher_vivo_default_action
        else R.string.set_default_launcher_later
    )
    dialogView.findViewById<TextView>(R.id.tv_primary_label).text = getString(
        when {
            hasSecondaryStep -> R.string.set_default_launcher_vivo_security_action
            profile.support == OemLauncherSupport.RESTRICTED -> R.string.set_default_launcher_view_settings
            else -> R.string.set_default_launcher_action
        }
    )
    dialogView.findViewById<ImageView>(R.id.iv_dialog_vendor_icon).apply {
        setImageDrawable(OemLauncherIconLoader.load(this@showSetDefaultLauncherDialog, profile))
        visibility = View.VISIBLE
    }

    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    if (hasSecondaryStep) {
        stackDialogActions(dialogView)
    }

    dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
        dialog.dismiss()
        if (hasSecondaryStep) {
            SettingsReturnCoordinator.markDeviceSettingsReturn(this)
            requestDefaultLauncherRole()
        }
    }
    dialogView.findViewById<View>(R.id.btn_open_settings).setOnClickListener {
        dialog.dismiss()
        when {
            secondarySettingsAction != null -> openSettingsAction(secondarySettingsAction)
            profile.support == OemLauncherSupport.RESTRICTED -> {
                SettingsReturnCoordinator.markDeviceSettingsReturn(this)
                openDefaultLauncherSettings()
            }
            else -> requestDefaultLauncherRole()
        }
    }
    dialog.show()
    return dialog
}

private fun SettingsActivity.stackDialogActions(dialogView: View) {
    val gap = (8 * resources.displayMetrics.density).toInt()
    dialogView.findViewById<LinearLayout>(R.id.layout_dialog_actions).orientation = LinearLayout.VERTICAL
    dialogView.findViewById<View>(R.id.btn_cancel).updateLayoutParams<LinearLayout.LayoutParams> {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        weight = 0f
        setMargins(0, 0, 0, gap)
        marginStart = 0
        marginEnd = 0
    }
    dialogView.findViewById<View>(R.id.btn_open_settings).updateLayoutParams<LinearLayout.LayoutParams> {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        weight = 0f
        setMargins(0, gap, 0, 0)
        marginStart = 0
        marginEnd = 0
    }
}

private fun SettingsActivity.openSettingsAction(action: String) {
    runCatching { startActivity(Intent(action)) }
        .onFailure { openSystemSettings() }
}

private fun OemLauncherProfile.messageRes(): Int = when (vendorKey) {
    "vivo" -> R.string.set_default_launcher_message_vivo
    "xiaomi" -> R.string.set_default_launcher_message_xiaomi
    "huawei" -> R.string.set_default_launcher_message_huawei
    "honor" -> R.string.set_default_launcher_message_honor
    "oplus" -> R.string.set_default_launcher_message_oplus
    "samsung" -> R.string.set_default_launcher_message_samsung
    else -> R.string.set_default_launcher_message
}

internal fun SettingsActivity.clearDefaultLauncher() {
    if (!isDefaultLauncher()) {
        Toast.makeText(this, getString(R.string.set_default_launcher_summary_off), Toast.LENGTH_SHORT).show()
        return
    }
    try {
        @Suppress("DEPRECATION")
        packageManager.clearPackagePreferredActivities(packageName)
    } catch (_: Exception) {
    }
    SettingsReturnCoordinator.markDeviceSettingsReturn(this)
    openDefaultLauncherSettings()
    Toast.makeText(this, getString(R.string.clear_default_launcher_choose_other), Toast.LENGTH_LONG).show()
}

internal fun SettingsActivity.requestDefaultLauncherRole() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requestDefaultLauncherRoleByRoleManager()) {
        return
    }
    openDefaultLauncherSettings()
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun SettingsActivity.requestDefaultLauncherRoleByRoleManager(): Boolean {
    val roleManager = getSystemService(RoleManager::class.java) ?: return false
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        return false
    }
    if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
        overviewController.refreshOverviewUi()
        Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
        return true
    }
    return runCatching {
        defaultLauncherRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        true
    }.getOrDefault(false)
}

internal fun SettingsActivity.openDefaultLauncherSettings() {
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
            DebugLog.w(TAG, "openDefaultLauncherSettings failed for ${intent.action}")
        }
    }
    Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
}
