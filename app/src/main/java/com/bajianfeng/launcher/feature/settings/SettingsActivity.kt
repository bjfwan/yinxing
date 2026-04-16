package com.bajianfeng.launcher.feature.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.card.MaterialCardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.home.LauncherPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var lowPerformanceSwitch: SwitchCompat
    private lateinit var lowPerformanceSummary: TextView

    // 权限状态 TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilitySummary: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvNotificationSummary: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvBatterySummary: TextView
    private lateinit var tvAutostartStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlaySummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        launcherPreferences = LauncherPreferences.getInstance(this)

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

        // 绑定权限状态 View
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvAccessibilitySummary = findViewById(R.id.tv_accessibility_summary)
        tvNotificationStatus = findViewById(R.id.tv_notification_listener_status)
        tvNotificationSummary = findViewById(R.id.tv_notification_listener_summary)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvBatterySummary = findViewById(R.id.tv_battery_summary)
        tvAutostartStatus = findViewById(R.id.tv_autostart_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvOverlaySummary = findViewById(R.id.tv_overlay_summary)

        // 权限入口点击
        findViewById<MaterialCardView>(R.id.btn_accessibility).setOnClickListener {
            PermissionUtil.openAccessibilitySettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_notification_listener).setOnClickListener {
            PermissionUtil.openNotificationListenerSettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_battery).setOnClickListener {
            PermissionUtil.openBatteryOptimizationSettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_autostart).setOnClickListener {
            PermissionUtil.openAutoStartSettings(this)
        }
        findViewById<MaterialCardView>(R.id.btn_overlay).setOnClickListener {
            PermissionUtil.openOverlaySettings(this)
        }

        // 系统设置
        findViewById<MaterialCardView>(R.id.btn_system_settings).setOnClickListener {
            openSystemSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAllPermissionUi()
    }

    // ── 低性能模式 ────────────────────────────────────────────────────────────

    private fun updateLowPerformanceSummary(enabled: Boolean) {
        lowPerformanceSummary.text = getString(
            if (enabled) R.string.settings_low_performance_summary_on
            else R.string.settings_low_performance_summary_off
        )
    }

    // ── 权限状态刷新 ──────────────────────────────────────────────────────────

    private fun refreshAllPermissionUi() {
        // 无障碍
        val accessibilityGranted = PermissionUtil.isAnyAccessibilityServiceEnabled(this)
        setStatusBadge(tvAccessibilityStatus, accessibilityGranted)
        tvAccessibilitySummary.text = getString(
            if (accessibilityGranted) R.string.settings_accessibility_summary_on
            else R.string.settings_accessibility_summary_off
        )

        // 通知监听
        val notificationGranted = PermissionUtil.isNotificationListenerEnabled(this)
        setStatusBadge(tvNotificationStatus, notificationGranted)
        tvNotificationSummary.text = getString(
            if (notificationGranted) R.string.settings_notification_listener_summary_on
            else R.string.settings_notification_listener_summary_off
        )

        // 电池优化豁免
        val batteryGranted = PermissionUtil.isIgnoringBatteryOptimizations(this)
        setStatusBadge(tvBatteryStatus, batteryGranted)
        tvBatterySummary.text = getString(
            if (batteryGranted) R.string.settings_battery_summary_on
            else R.string.settings_battery_summary_off
        )

        // 自启动（无 API 可查，固定显示"去设置"）
        tvAutostartStatus.text = getString(R.string.settings_permission_go_set)
        tvAutostartStatus.setTextColor(getColor(R.color.launcher_action))

        // 悬浮窗
        val overlayGranted = PermissionUtil.canDrawOverlays(this)
        setStatusBadge(tvOverlayStatus, overlayGranted)
        tvOverlaySummary.text = getString(
            if (overlayGranted) R.string.settings_overlay_summary_on
            else R.string.settings_overlay_summary_off
        )
    }

    /**
     * 统一设置绿色"已授权" / 红色"未授权"状态标签。
     */
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
}
