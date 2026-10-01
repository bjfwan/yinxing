package com.yinxing.launcher.feature.settings

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.LauncherDialogFactory
import com.yinxing.launcher.data.weather.WeatherRepository

internal fun SettingsActivity.showSystemDialog() {
    val dialog = createListDialog(
        title = getString(R.string.settings_section_system_title),
        message = getString(R.string.settings_dialog_system_message)
    )
    addDialogEntry(
        context = dialog,
        title = getString(R.string.settings_weather_city_title),
        summary = getString(R.string.settings_weather_city_summary, weatherPreferences.getCityName()),
        badge = actionBadge(getString(R.string.settings_entry_modify)),
        iconResId = R.drawable.ic_weather_sun,
        iconTintResId = R.color.launcher_warning,
        iconPlateResId = R.color.launcher_warning_soft
    ) {
        dialog.dialog.dismiss()
        showSetCityDialog()
    }
    addDialogEntry(
        context = dialog,
        title = getString(R.string.settings_system_title),
        summary = getString(R.string.settings_system_summary),
        badge = actionBadge(getString(R.string.settings_entry_open_settings)),
        iconResId = R.drawable.ic_settings_category_system,
        iconTintResId = R.color.launcher_system,
        iconPlateResId = R.color.launcher_system_soft
    ) {
        dialog.dialog.dismiss()
        actionController.openSystemSettings()
    }
    val knownUpdate = AppUpdateStore(this).latestAvailable()
    addDialogEntry(
        context = dialog,
        title = getString(R.string.settings_update_title),
        summary = knownUpdate?.let { getString(R.string.settings_update_found_new, it.versionName) }
            ?: getString(R.string.settings_update_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        badge = actionBadge(getString(R.string.settings_update_check)),
        iconResId = R.drawable.ic_settings_action_update,
        iconTintResId = R.color.launcher_contacts,
        iconPlateResId = R.color.launcher_contacts_soft
    ) {
        dialog.dialog.dismiss()
        startActivity(AppUpdateActivity.createIntent(this))
    }
    dialog.dialog.show()
}

internal fun SettingsActivity.showSetCityDialog() {
    val currentCity = weatherPreferences.getCityName()
    val dialogView = layoutInflater.inflate(R.layout.dialog_set_city, null)
    val etCity = dialogView.findViewById<EditText>(R.id.et_city)
    etCity.setText(currentCity)
    etCity.setSelection(currentCity.length)

    val dialog = LauncherDialogFactory.create(this, dialogView, dismissOnTouchOutside = false)

    dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.btn_cancel)
        .setOnClickListener { dialog.dismiss() }

    val confirm = {
        val city = etCity.text.toString().trim()
        if (city.isNotEmpty()) {
            dialog.dismiss()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etCity.windowToken, 0)
            weatherPreferences.setCityName(city)
            WeatherRepository.clearCache()
            overviewController.updateSystemHubCard()
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etCity, InputMethodManager.SHOW_IMPLICIT)
    }, 100)
}
