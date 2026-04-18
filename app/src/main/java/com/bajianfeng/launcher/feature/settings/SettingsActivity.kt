package com.bajianfeng.launcher.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.data.weather.WeatherPreferences
import com.bajianfeng.launcher.data.weather.WeatherRepository
import com.bajianfeng.launcher.feature.appmanage.AppManageActivity
import com.bajianfeng.launcher.feature.incoming.IncomingCallDiagnostics
import com.bajianfeng.launcher.feature.incoming.IncomingGuardItem
import com.bajianfeng.launcher.feature.incoming.IncomingGuardItemState
import com.bajianfeng.launcher.feature.incoming.IncomingGuardReadiness
import com.bajianfeng.launcher.feature.incoming.IncomingGuardReadinessEvaluator
import com.bajianfeng.launcher.feature.phone.PhoneContactActivity
import com.bajianfeng.launcher.feature.videocall.VideoCallActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var weatherPreferences: WeatherPreferences

    private lateinit var tvIncomingGuardStatus: TextView
    private lateinit var tvIncomingGuardProgress: TextView
    private lateinit var tvIncomingGuardSummary: TextView
    private lateinit var tvIncomingGuardAction: TextView
    private lateinit var btnIncomingGuardAction: View

    private lateinit var lowPerformanceSwitch: SwitchCompat
    private lateinit var lowPerformanceSummary: TextView
    private lateinit var kioskModeSwitch: SwitchCompat
    private lateinit var kioskModeSummary: TextView
    private lateinit var autoAnswerSwitch: SwitchCompat
    private lateinit var autoAnswerSummary: TextView
    private lateinit var autoAnswerDelaySummary: TextView
    private lateinit var autoAnswerDelayMinus: View
    private lateinit var autoAnswerDelayPlus: View
    private lateinit var tvIncomingTraceSummary: TextView
    private lateinit var tvWeatherCitySummary: TextView

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilitySummary: TextView
    private lateinit var tvNotificationPermissionStatus: TextView
    private lateinit var tvNotificationPermissionSummary: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvBatterySummary: TextView
    private lateinit var tvAutostartStatus: TextView
    private lateinit var tvAutostartSummary: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlaySummary: TextView
    private lateinit var tvPhonePermissionStatus: TextView
    private lateinit var tvPhonePermissionSummary: TextView
    private lateinit var tvDefaultLauncherStatus: TextView
    private lateinit var tvDefaultLauncherSummary: TextView
    private lateinit var tvBgStartStatus: TextView
    private lateinit var tvBgStartSummary: TextView

    private var incomingGuardReadiness = IncomingGuardReadiness(emptyList())

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
        refreshAllPermissionUi()
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
        refreshAllPermissionUi()
    }

    private val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshAllPermissionUi()
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

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        bindQuickSetupSection()
        bindIncomingGuardSection()
        bindLauncherSection()
        bindCallSection()
        bindSupportSection()
        bindOtherSection()
    }

    override fun onResume() {
        super.onResume()
        refreshAllPermissionUi()
        updateWeatherCitySummary()
        updateAutoAnswerDelaySummary(launcherPreferences.getAutoAnswerDelaySeconds())
        updateIncomingTraceSummary()
    }

    private fun bindQuickSetupSection() {
        findViewById<View>(R.id.btn_manage_phone_contacts).setOnClickListener {
            startActivity(PhoneContactActivity.createIntent(this, startInManageMode = true))
        }
        findViewById<View>(R.id.btn_manage_video_contacts).setOnClickListener {
            startActivity(VideoCallActivity.createIntent(this, startInManageMode = true))
        }
        findViewById<View>(R.id.btn_manage_home_apps).setOnClickListener {
            startActivity(Intent(this, AppManageActivity::class.java))
        }
    }

    private fun bindIncomingGuardSection() {
        tvIncomingGuardStatus = findViewById(R.id.tv_incoming_guard_status)
        tvIncomingGuardProgress = findViewById(R.id.tv_incoming_guard_progress)
        tvIncomingGuardSummary = findViewById(R.id.tv_incoming_guard_summary)
        tvIncomingGuardAction = findViewById(R.id.tv_incoming_guard_action)
        btnIncomingGuardAction = findViewById(R.id.btn_incoming_guard_action)
        btnIncomingGuardAction.setOnClickListener {
            incomingGuardReadiness.blocker?.item?.let(::openIncomingGuardItem)
        }

        tvPhonePermissionStatus = findViewById(R.id.tv_phone_permission_status)
        tvPhonePermissionSummary = findViewById(R.id.tv_phone_permission_summary)
        findViewById<View>(R.id.btn_phone_permission).setOnClickListener {
            openIncomingGuardItem(IncomingGuardItem.PhonePermission)
        }

        tvNotificationPermissionStatus = findViewById(R.id.tv_notification_permission_status)
        tvNotificationPermissionSummary = findViewById(R.id.tv_notification_permission_summary)
        findViewById<View>(R.id.btn_notification_permission).setOnClickListener {
            openIncomingGuardItem(IncomingGuardItem.NotificationPermission)
        }

        tvDefaultLauncherStatus = findViewById(R.id.tv_default_launcher_status)
        tvDefaultLauncherSummary = findViewById(R.id.tv_default_launcher_summary)
        findViewById<View>(R.id.btn_set_default_launcher).setOnClickListener {
            openIncomingGuardItem(IncomingGuardItem.DefaultLauncher)
        }

        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvBatterySummary = findViewById(R.id.tv_battery_summary)
        findViewById<View>(R.id.btn_battery).setOnClickListener {
            openIncomingGuardItem(IncomingGuardItem.BatteryOptimization)
        }

        tvAutostartStatus = findViewById(R.id.tv_autostart_status)
        tvAutostartSummary = findViewById(R.id.tv_autostart_summary)
        findViewById<View>(R.id.btn_autostart).setOnClickListener {
            openIncomingGuardItem(IncomingGuardItem.AutoStart)
        }

        tvBgStartStatus = findViewById(R.id.tv_bg_start_status)
        tvBgStartSummary = findViewById(R.id.tv_bg_start_summary)
        findViewById<View>(R.id.btn_bg_start).setOnClickListener {
            openIncomingGuardItem(IncomingGuardItem.BackgroundStart)
        }
    }

    private fun bindLauncherSection() {
        lowPerformanceSwitch = findViewById(R.id.switch_low_performance)
        lowPerformanceSummary = findViewById(R.id.tv_low_performance_summary)
        lowPerformanceSwitch.isChecked = launcherPreferences.isLowPerformanceModeEnabled()
        updateLowPerformanceSummary(lowPerformanceSwitch.isChecked)
        lowPerformanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPreferences.setLowPerformanceModeEnabled(isChecked)
            updateLowPerformanceSummary(isChecked)
        }

        kioskModeSwitch = findViewById(R.id.switch_kiosk_mode)
        kioskModeSummary = findViewById(R.id.tv_kiosk_mode_summary)
        kioskModeSwitch.isChecked = launcherPreferences.isKioskModeEnabled()
        updateKioskModeSummary(kioskModeSwitch.isChecked)
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
            updateKioskModeSummary(isChecked)
        }
    }

    private fun bindCallSection() {
        autoAnswerSwitch = findViewById(R.id.switch_auto_answer)
        autoAnswerSummary = findViewById(R.id.tv_auto_answer_summary)
        autoAnswerDelaySummary = findViewById(R.id.tv_auto_answer_delay_summary)
        autoAnswerDelayMinus = findViewById(R.id.btn_auto_answer_delay_minus)
        autoAnswerDelayPlus = findViewById(R.id.btn_auto_answer_delay_plus)
        tvIncomingTraceSummary = findViewById(R.id.tv_incoming_trace_summary)

        val autoAnswerEnabled = launcherPreferences.isAutoAnswerEnabled()
        autoAnswerSwitch.isChecked = autoAnswerEnabled
        updateAutoAnswerSummary(autoAnswerEnabled)
        updateAutoAnswerDelaySummary(launcherPreferences.getAutoAnswerDelaySeconds())
        updateAutoAnswerDelayControls(autoAnswerEnabled)
        updateIncomingTraceSummary()

        autoAnswerSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPreferences.setAutoAnswerEnabled(isChecked)
            updateAutoAnswerSummary(isChecked)
            updateAutoAnswerDelayControls(isChecked)
        }

        autoAnswerDelayMinus.setOnClickListener { adjustAutoAnswerDelay(-1) }
        autoAnswerDelayPlus.setOnClickListener { adjustAutoAnswerDelay(1) }
    }

    private fun bindSupportSection() {
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvAccessibilitySummary = findViewById(R.id.tv_accessibility_summary)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvOverlaySummary = findViewById(R.id.tv_overlay_summary)

        findViewById<View>(R.id.btn_accessibility).setOnClickListener {
            PermissionUtil.openAccessibilitySettings(this)
        }
        findViewById<View>(R.id.btn_overlay).setOnClickListener {
            PermissionUtil.openOverlaySettings(this)
        }
    }

    private fun bindOtherSection() {
        tvWeatherCitySummary = findViewById(R.id.tv_weather_city_summary)
        updateWeatherCitySummary()
        findViewById<View>(R.id.btn_weather_city).setOnClickListener {
            showSetCityDialog()
        }
        findViewById<View>(R.id.btn_system_settings).setOnClickListener {
            openSystemSettings()
        }
    }

    private fun updateLowPerformanceSummary(enabled: Boolean) {
        lowPerformanceSummary.text = getString(
            if (enabled) R.string.settings_low_performance_summary_on
            else R.string.settings_low_performance_summary_off
        )
    }

    private fun updateKioskModeSummary(enabled: Boolean) {
        kioskModeSummary.text = getString(
            if (enabled) R.string.settings_kiosk_mode_summary_on
            else R.string.settings_kiosk_mode_summary_off
        )
    }

    private fun updateAutoAnswerSummary(enabled: Boolean) {
        autoAnswerSummary.text = getString(
            if (enabled) R.string.settings_auto_answer_summary_on
            else R.string.settings_auto_answer_summary_off
        )
    }

    private fun updateAutoAnswerDelaySummary(seconds: Int) {
        autoAnswerDelaySummary.text = getString(R.string.settings_auto_answer_delay_summary, seconds)
    }

    private fun updateAutoAnswerDelayControls(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.38f
        listOf(autoAnswerDelayMinus, autoAnswerDelayPlus, autoAnswerDelaySummary).forEach { view ->
            view.isEnabled = enabled
            view.alpha = alpha
        }
    }

    private fun adjustAutoAnswerDelay(delta: Int) {
        if (!autoAnswerSwitch.isChecked) return
        val updated = launcherPreferences.getAutoAnswerDelaySeconds() + delta
        launcherPreferences.setAutoAnswerDelaySeconds(updated)
        updateAutoAnswerDelaySummary(launcherPreferences.getAutoAnswerDelaySeconds())
    }

    private fun updateWeatherCitySummary() {
        tvWeatherCitySummary.text = getString(
            R.string.settings_weather_city_summary,
            weatherPreferences.getCityName()
        )
    }

    private fun updateIncomingTraceSummary() {
        tvIncomingTraceSummary.text = IncomingCallDiagnostics.getDisplayText(this)
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
                updateWeatherCitySummary()
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

    private fun refreshAllPermissionUi() {
        val accessibilityGranted = PermissionUtil.isAnyAccessibilityServiceEnabled(this)
        setStatusBadge(tvAccessibilityStatus, accessibilityGranted)
        tvAccessibilitySummary.text = getString(
            if (accessibilityGranted) R.string.settings_accessibility_summary_on
            else R.string.settings_accessibility_summary_off
        )

        val overlayGranted = PermissionUtil.canDrawOverlays(this)
        setStatusBadge(tvOverlayStatus, overlayGranted)
        tvOverlaySummary.text = getString(
            if (overlayGranted) R.string.settings_overlay_summary_on
            else R.string.settings_overlay_summary_off
        )

        refreshIncomingGuardUi()
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
            tvIncomingGuardAction.text = getString(R.string.settings_incoming_guard_action_ready)
            btnIncomingGuardAction.isEnabled = false
            btnIncomingGuardAction.alpha = 0.56f
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
            btnIncomingGuardAction.isEnabled = true
            btnIncomingGuardAction.alpha = 1f
        }

        incomingGuardReadiness.items.forEach { itemState ->
            val isBlocker = blocker == itemState.item
            when (itemState.item) {
                IncomingGuardItem.PhonePermission -> renderGuardItem(
                    itemState,
                    tvPhonePermissionSummary,
                    tvPhonePermissionStatus,
                    isBlocker
                )
                IncomingGuardItem.NotificationPermission -> renderGuardItem(
                    itemState,
                    tvNotificationPermissionSummary,
                    tvNotificationPermissionStatus,
                    isBlocker
                )
                IncomingGuardItem.DefaultLauncher -> renderGuardItem(
                    itemState,
                    tvDefaultLauncherSummary,
                    tvDefaultLauncherStatus,
                    isBlocker
                )
                IncomingGuardItem.BatteryOptimization -> renderGuardItem(
                    itemState,
                    tvBatterySummary,
                    tvBatteryStatus,
                    isBlocker
                )
                IncomingGuardItem.AutoStart -> renderGuardItem(
                    itemState,
                    tvAutostartSummary,
                    tvAutostartStatus,
                    isBlocker
                )
                IncomingGuardItem.BackgroundStart -> renderGuardItem(
                    itemState,
                    tvBgStartSummary,
                    tvBgStartStatus,
                    isBlocker
                )
            }
        }
    }

    private fun renderGuardItem(
        itemState: IncomingGuardItemState,
        summaryView: TextView,
        statusView: TextView,
        isBlocker: Boolean
    ) {
        val baseSummary = guardSummary(itemState)
        summaryView.text = if (isBlocker) {
            getString(R.string.settings_guard_blocker_prefix, baseSummary)
        } else {
            baseSummary
        }
        when {
            itemState.isReady && itemState.requiresManualConfirmation -> {
                applyInfoBadge(
                    tv = statusView,
                    text = getString(R.string.settings_guard_status_confirmed),
                    textColorResId = R.color.launcher_action_dark,
                    backgroundColorResId = R.color.launcher_primary_soft
                )
            }
            itemState.isReady -> {
                applyInfoBadge(
                    tv = statusView,
                    text = getString(R.string.settings_guard_status_done),
                    textColorResId = R.color.launcher_action_dark,
                    backgroundColorResId = R.color.launcher_primary_soft
                )
            }
            isBlocker -> {
                applyInfoBadge(
                    tv = statusView,
                    text = getString(R.string.settings_guard_status_blocking),
                    textColorResId = R.color.launcher_warning,
                    backgroundColorResId = R.color.launcher_warning_soft
                )
            }
            itemState.requiresManualConfirmation -> {
                applyInfoBadge(
                    tv = statusView,
                    text = getString(R.string.settings_guard_status_pending),
                    textColorResId = R.color.launcher_warning,
                    backgroundColorResId = R.color.launcher_warning_soft
                )
            }
            else -> {
                applyInfoBadge(
                    tv = statusView,
                    text = getString(R.string.settings_permission_go_set),
                    textColorResId = R.color.launcher_action,
                    backgroundColorResId = R.color.launcher_surface_muted
                )
            }
        }
    }

    private fun guardSummary(itemState: IncomingGuardItemState): String {
        return when (itemState.item) {
            IncomingGuardItem.PhonePermission -> getString(
                if (itemState.isReady) R.string.settings_phone_permission_summary_on
                else R.string.settings_phone_permission_summary_off
            )
            IncomingGuardItem.NotificationPermission -> getString(
                if (itemState.isReady) R.string.settings_notification_permission_summary_on
                else R.string.settings_notification_permission_summary_off
            )
            IncomingGuardItem.DefaultLauncher -> getString(
                if (itemState.isReady) R.string.set_default_launcher_summary_on
                else R.string.set_default_launcher_summary_off
            )
            IncomingGuardItem.BatteryOptimization -> getString(
                if (itemState.isReady) R.string.settings_battery_summary_on
                else R.string.settings_battery_summary_off
            )
            IncomingGuardItem.AutoStart -> getString(
                if (itemState.isReady) R.string.settings_autostart_summary_on
                else R.string.settings_autostart_summary_off
            )
            IncomingGuardItem.BackgroundStart -> getString(
                if (itemState.isReady) R.string.settings_bg_start_summary_on
                else R.string.settings_bg_start_summary_off
            )
        }
    }

    private fun guardTitle(item: IncomingGuardItem): String {
        return getString(
            when (item) {
                IncomingGuardItem.PhonePermission -> R.string.settings_phone_permission_title
                IncomingGuardItem.NotificationPermission -> R.string.settings_notification_permission_title
                IncomingGuardItem.DefaultLauncher -> R.string.set_default_launcher_title
                IncomingGuardItem.BatteryOptimization -> R.string.settings_battery_title
                IncomingGuardItem.AutoStart -> R.string.settings_autostart_title
                IncomingGuardItem.BackgroundStart -> R.string.settings_bg_start_title
            }
        )
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
        refreshAllPermissionUi()
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

    private fun setStatusBadge(tv: TextView, granted: Boolean) {
        applyInfoBadge(
            tv = tv,
            text = getString(
                if (granted) R.string.settings_permission_status_granted
                else R.string.settings_permission_status_denied
            ),
            textColorResId = if (granted) R.color.launcher_action_dark else R.color.launcher_danger,
            backgroundColorResId = if (granted) R.color.launcher_primary_soft else R.color.launcher_danger_soft
        )
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
            refreshAllPermissionUi()
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
            }
        }
        Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
    }
}
