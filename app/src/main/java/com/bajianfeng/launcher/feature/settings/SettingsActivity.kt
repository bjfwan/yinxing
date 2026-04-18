package com.bajianfeng.launcher.feature.settings

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.card.MaterialCardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.home.LauncherPreferences
import com.bajianfeng.launcher.data.weather.WeatherPreferences
import com.bajianfeng.launcher.data.weather.WeatherRepository

class SettingsActivity : AppCompatActivity() {

    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var weatherPreferences: WeatherPreferences
    private lateinit var lowPerformanceSwitch: SwitchCompat
    private lateinit var lowPerformanceSummary: TextView
    private lateinit var kioskModeSwitch: SwitchCompat
    private lateinit var kioskModeSummary: TextView
    private lateinit var tvWeatherCitySummary: TextView

    // 权限状态 TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilitySummary: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvBatterySummary: TextView
    private lateinit var tvAutostartStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlaySummary: TextView
    private lateinit var tvPhonePermissionStatus: TextView
    private lateinit var tvPhonePermissionSummary: TextView
    private lateinit var tvDefaultLauncherStatus: TextView
    private lateinit var tvDefaultLauncherSummary: TextView
    private lateinit var tvBgStartStatus: TextView

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            Toast.makeText(this, getString(R.string.settings_phone_permission_granted_toast), Toast.LENGTH_SHORT).show()
        } else {
            // 用户拒绝了，引导去设置手动开
            PermissionUtil.openAppDetailSettings(this)
        }
        refreshAllPermissionUi()
    }

    private val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshDefaultLauncherUi()
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

        // 返回
        findViewById<MaterialCardView>(R.id.btn_back).setOnClickListener { finish() }

        // 低性能模式
        lowPerformanceSwitch = findViewById(R.id.switch_low_performance)
        lowPerformanceSummary = findViewById(R.id.tv_low_performance_summary)
        lowPerformanceSwitch.isChecked = launcherPreferences.isLowPerformanceModeEnabled()
        updateLowPerformanceSummary(lowPerformanceSwitch.isChecked)
        lowPerformanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPreferences.setLowPerformanceModeEnabled(isChecked)
            updateLowPerformanceSummary(isChecked)
        }

        // 防退出模式
        kioskModeSwitch = findViewById(R.id.switch_kiosk_mode)
        kioskModeSummary = findViewById(R.id.tv_kiosk_mode_summary)
        kioskModeSwitch.isChecked = launcherPreferences.isKioskModeEnabled()
        updateKioskModeSummary(kioskModeSwitch.isChecked)
        kioskModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isDefaultLauncher()) {
                // 未设为默认桌面，拦截开关，提示先去设置
                kioskModeSwitch.isChecked = false
                Toast.makeText(this, "请先将本应用设为默认桌面，再开启防退出模式", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            launcherPreferences.setKioskModeEnabled(isChecked)
            updateKioskModeSummary(isChecked)
        }

        // 绑定权限状态 View
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvAccessibilitySummary = findViewById(R.id.tv_accessibility_summary)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvBatterySummary = findViewById(R.id.tv_battery_summary)
        tvAutostartStatus = findViewById(R.id.tv_autostart_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvOverlaySummary = findViewById(R.id.tv_overlay_summary)
        tvPhonePermissionStatus = findViewById(R.id.tv_phone_permission_status)
        tvPhonePermissionSummary = findViewById(R.id.tv_phone_permission_summary)
        tvBgStartStatus = findViewById(R.id.tv_bg_start_status)

        // 设为默认桌面
        tvDefaultLauncherStatus = findViewById(R.id.tv_default_launcher_status)
        tvDefaultLauncherSummary = findViewById(R.id.tv_default_launcher_summary)
        findViewById<MaterialCardView>(R.id.btn_set_default_launcher).setOnClickListener {
            if (isDefaultLauncher()) {
                Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
            } else {
                showSetDefaultLauncherDialog()
            }
        }

        // 权限入口点击
        findViewById<MaterialCardView>(R.id.btn_accessibility).setOnClickListener {
            PermissionUtil.openAccessibilitySettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_battery).setOnClickListener {
            PermissionUtil.openBatteryOptimizationSettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_autostart).setOnClickListener {
            PermissionUtil.openAutoStartSettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_bg_start).setOnClickListener {
            PermissionUtil.openBackgroundStartSettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_overlay).setOnClickListener {
            PermissionUtil.openOverlaySettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_phone_permission).setOnClickListener {
            if (PermissionUtil.hasPhonePermission(this)) {
                Toast.makeText(this, getString(R.string.settings_phone_permission_granted_toast), Toast.LENGTH_SHORT).show()
            } else {
                val perms = mutableListOf(
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.READ_CALL_LOG
                ).apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        add(android.Manifest.permission.ANSWER_PHONE_CALLS)
                    }
                }
                phonePermissionLauncher.launch(perms.toTypedArray())
            }
        }

        // 天气城市
        tvWeatherCitySummary = findViewById(R.id.tv_weather_city_summary)
        updateWeatherCitySummary()
        findViewById<MaterialCardView>(R.id.btn_weather_city).setOnClickListener {
            showSetCityDialog()
        }

        // 系统设置
        findViewById<MaterialCardView>(R.id.btn_system_settings).setOnClickListener {
            openSystemSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAllPermissionUi()
        updateWeatherCitySummary()
        refreshDefaultLauncherUi()
    }

    // ── 低性能模式 ────────────────────────────────────────────────────────────

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

    // ── 天气城市 ──────────────────────────────────────────────────────────────

    private fun updateWeatherCitySummary() {
        tvWeatherCitySummary.text = getString(
            R.string.settings_weather_city_summary,
            weatherPreferences.getCityName()
        )
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
                Toast.makeText(this, "城市已更新为：$city", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请输入城市名", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_confirm)
            .setOnClickListener { confirm() }

        etCity.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { confirm(); true } else false
        }

        dialog.show()
        etCity.postDelayed({
            etCity.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etCity, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    // ── 权限状态刷新 ──────────────────────────────────────────────────────────

    private fun refreshAllPermissionUi() {
        val accessibilityGranted = PermissionUtil.isAnyAccessibilityServiceEnabled(this)
        setStatusBadge(tvAccessibilityStatus, accessibilityGranted)
        tvAccessibilitySummary.text = getString(
            if (accessibilityGranted) R.string.settings_accessibility_summary_on
            else R.string.settings_accessibility_summary_off
        )

        val batteryGranted = PermissionUtil.isIgnoringBatteryOptimizations(this)
        setStatusBadge(tvBatteryStatus, batteryGranted)
        tvBatterySummary.text = getString(
            if (batteryGranted) R.string.settings_battery_summary_on
            else R.string.settings_battery_summary_off
        )

        tvAutostartStatus.text = getString(R.string.settings_permission_go_set)
        tvAutostartStatus.setTextColor(getColor(R.color.launcher_action))

        val overlayGranted = PermissionUtil.canDrawOverlays(this)
        setStatusBadge(tvOverlayStatus, overlayGranted)
        tvOverlaySummary.text = getString(
            if (overlayGranted) R.string.settings_overlay_summary_on
            else R.string.settings_overlay_summary_off
        )

        val phoneGranted = PermissionUtil.hasPhonePermission(this)
        setStatusBadge(tvPhonePermissionStatus, phoneGranted)
        tvPhonePermissionSummary.text = getString(
            if (phoneGranted) R.string.settings_phone_permission_summary_on
            else R.string.settings_phone_permission_summary_off
        )

        // 后台弹出界面：无法检测，始终显示"去设置"
        tvBgStartStatus.text = getString(R.string.settings_permission_go_set)
        tvBgStartStatus.setTextColor(getColor(R.color.launcher_action))
    }

    private fun setStatusBadge(tv: TextView, granted: Boolean) {
        tv.text = getString(
            if (granted) R.string.settings_permission_status_granted
            else R.string.settings_permission_status_denied
        )
        tv.setTextColor(
            getColor(
                if (granted) R.color.launcher_action_dark
                else R.color.launcher_danger
            )
        )
    }

    // ── 系统设置 ──────────────────────────────────────────────────────────────

    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // ── 默认桌面引导 ──────────────────────────────────────────────────────────

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun refreshDefaultLauncherUi() {
        val isDefault = isDefaultLauncher()
        setStatusBadge(tvDefaultLauncherStatus, isDefault)
        tvDefaultLauncherSummary.text = getString(
            if (isDefault) R.string.set_default_launcher_summary_on
            else R.string.set_default_launcher_summary_off
        )
    }

    private fun showSetDefaultLauncherDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_default_launcher_title))
            .setMessage(getString(R.string.set_default_launcher_message))
            .setPositiveButton(getString(R.string.set_default_launcher_action)) { _, _ ->
                requestDefaultLauncherRole()
            }
            .setNegativeButton(getString(R.string.set_default_launcher_later), null)
            .show()
    }

    private fun requestDefaultLauncherRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                refreshDefaultLauncherUi()
                Toast.makeText(this, getString(R.string.set_default_launcher_summary_on), Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                defaultLauncherRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        openDefaultLauncherSettings()
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
                // 继续尝试下一个
            }
        }
        Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
    }
}
