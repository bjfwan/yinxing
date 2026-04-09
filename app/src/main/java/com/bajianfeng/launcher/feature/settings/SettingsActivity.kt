package com.bajianfeng.launcher.feature.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.home.LauncherPreferences

class SettingsActivity : AppCompatActivity() {
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var lowPerformanceSwitch: SwitchCompat
    private lateinit var lowPerformanceSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        launcherPreferences = LauncherPreferences.getInstance(this)
        lowPerformanceSwitch = findViewById(R.id.switch_low_performance)
        lowPerformanceSummary = findViewById(R.id.tv_low_performance_summary)

        findViewById<CardView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<CardView>(R.id.btn_system_settings).setOnClickListener {
            openSystemSettings()
        }

        lowPerformanceSwitch.isChecked = launcherPreferences.isLowPerformanceModeEnabled()
        updateLowPerformanceSummary(lowPerformanceSwitch.isChecked)
        lowPerformanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            launcherPreferences.setLowPerformanceModeEnabled(isChecked)
            updateLowPerformanceSummary(isChecked)
        }
    }

    private fun updateLowPerformanceSummary(enabled: Boolean) {
        lowPerformanceSummary.text = getString(
            if (enabled) R.string.settings_low_performance_summary_on
            else R.string.settings_low_performance_summary_off
        )
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
