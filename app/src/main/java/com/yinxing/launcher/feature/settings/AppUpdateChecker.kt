package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

internal sealed class AppUpdateState {
    data object UpToDate : AppUpdateState()
    data class Available(val info: AppUpdateInfo) : AppUpdateState()
    data class Failed(val message: String) : AppUpdateState()
}

internal class AppUpdateChecker(
    private val endpoint: String = "https://yx.likeyou.qzz.io/update.json"
) {
    suspend fun check(): AppUpdateState = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 7_000
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                return@withContext AppUpdateState.Failed("HTTP $status")
            }
            val json = JSONObject(body)
            val info = AppUpdateInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName"),
                apkUrl = json.optString("apkUrl"),
                releaseNotes = json.optString("releaseNotes")
            )
            if (info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotBlank()) {
                AppUpdateState.Available(info)
            } else {
                AppUpdateState.UpToDate
            }
        } catch (e: Exception) {
            AppUpdateState.Failed(e.message ?: "unknown")
        } finally {
            connection?.disconnect()
        }
    }
}
