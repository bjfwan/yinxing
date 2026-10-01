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
    val apkUrlFallback: String = "",
    val extraUrls: List<String> = emptyList(),
    val sha256: String = "",
    val forceBelowVersionCode: Int = 0,
    val releaseNotes: String = "",
    val announcementTitle: String = "",
    val announcementBody: String = ""
) {
    val downloadUrls: List<String>
        get() = (listOf(apkUrl) + extraUrls + apkUrlFallback)
            .filter { it.isNotBlank() }
            .distinct()

    fun isForced(currentVersionCode: Int): Boolean = forceBelowVersionCode > currentVersionCode

    fun hasAnnouncement(): Boolean = announcementTitle.isNotBlank() || announcementBody.isNotBlank()
}

internal sealed class AppUpdateState {
    data class UpToDate(
        val announcementTitle: String = "",
        val announcementBody: String = ""
    ) : AppUpdateState() {
        fun hasAnnouncement(): Boolean = announcementTitle.isNotBlank() || announcementBody.isNotBlank()
    }
    data class Available(val info: AppUpdateInfo) : AppUpdateState()
    data class Failed(val message: String) : AppUpdateState()
}

internal const val DEFAULT_APP_UPDATE_ENDPOINT = "https://yinxing.722688.xyz/update.json"

internal class AppUpdateChecker(
    private val endpoint: String = DEFAULT_APP_UPDATE_ENDPOINT
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
            val info = parseInfo(JSONObject(body))
            if (info.versionCode > BuildConfig.VERSION_CODE && info.downloadUrls.isNotEmpty()) {
                AppUpdateState.Available(info)
            } else {
                AppUpdateState.UpToDate(info.announcementTitle, info.announcementBody)
            }
        } catch (e: Exception) {
            AppUpdateState.Failed(e.message ?: "unknown")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseInfo(json: JSONObject): AppUpdateInfo {
        val announcement = json.optJSONObject("announcement")
        val urlsArray = json.optJSONArray("apkUrls")
        val extraUrls = List(urlsArray?.length() ?: 0) { urlsArray!!.optString(it) }
            .filter { it.isNotBlank() }
        return AppUpdateInfo(
            versionCode = json.optInt("versionCode", 0),
            versionName = json.optString("versionName"),
            apkUrl = json.optString("apkUrl"),
            apkUrlFallback = json.optString("apkUrlFallback"),
            extraUrls = extraUrls,
            sha256 = json.optString("sha256"),
            forceBelowVersionCode = json.optInt("forceBelowVersionCode", 0),
            releaseNotes = json.optString("releaseNotes"),
            announcementTitle = announcement?.optString("title").orEmpty(),
            announcementBody = announcement?.optString("body").orEmpty()
        )
    }
}
