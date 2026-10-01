package com.yinxing.launcher.feature.settings

import android.content.Context
import com.yinxing.launcher.BuildConfig
import org.json.JSONArray

internal class AppUpdateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()
        }

    fun shouldCheckNow(now: Long = System.currentTimeMillis()): Boolean =
        now - lastCheckAt >= CHECK_INTERVAL_MS

    fun saveAvailable(info: AppUpdateInfo) {
        prefs.edit()
            .putInt(KEY_VERSION_CODE, info.versionCode)
            .putString(KEY_VERSION_NAME, info.versionName)
            .putString(KEY_APK_URL, info.apkUrl)
            .putString(KEY_APK_URL_FALLBACK, info.apkUrlFallback)
            .putString(KEY_APK_URLS, JSONArray(info.extraUrls).toString())
            .putString(KEY_SHA256, info.sha256)
            .putInt(KEY_FORCE_BELOW, info.forceBelowVersionCode)
            .putString(KEY_RELEASE_NOTES, info.releaseNotes)
            .apply()
    }

    fun latestAvailable(): AppUpdateInfo? {
        val versionCode = prefs.getInt(KEY_VERSION_CODE, 0)
        if (versionCode <= BuildConfig.VERSION_CODE) return null
        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = prefs.getString(KEY_VERSION_NAME, "").orEmpty(),
            apkUrl = prefs.getString(KEY_APK_URL, "").orEmpty(),
            apkUrlFallback = prefs.getString(KEY_APK_URL_FALLBACK, "").orEmpty(),
            extraUrls = runCatching {
                val array = JSONArray(prefs.getString(KEY_APK_URLS, "[]").orEmpty())
                List(array.length()) { array.optString(it) }
            }.getOrDefault(emptyList()),
            sha256 = prefs.getString(KEY_SHA256, "").orEmpty(),
            forceBelowVersionCode = prefs.getInt(KEY_FORCE_BELOW, 0),
            releaseNotes = prefs.getString(KEY_RELEASE_NOTES, "").orEmpty()
        ).takeIf { it.downloadUrls.isNotEmpty() }
    }

    private companion object {
        const val PREFS_NAME = "app_update"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_VERSION_CODE = "available_version_code"
        const val KEY_VERSION_NAME = "available_version_name"
        const val KEY_APK_URL = "available_apk_url"
        const val KEY_APK_URL_FALLBACK = "available_apk_url_fallback"
        const val KEY_APK_URLS = "available_apk_urls"
        const val KEY_SHA256 = "available_sha256"
        const val KEY_FORCE_BELOW = "available_force_below"
        const val KEY_RELEASE_NOTES = "available_release_notes"
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }
}
