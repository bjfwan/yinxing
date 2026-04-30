package com.yinxing.launcher.common.lobster

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.yinxing.launcher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LobsterClient {
    private const val TAG = "LobsterClient"

    private val endpoint: String
        get() = BuildConfig.LOBSTER_UPLOAD_URL.trim().ifBlank {
            "https://tiny-lobster.2632507193.workers.dev/upload"
        }

    private val uploadToken: String
        get() = BuildConfig.LOBSTER_UPLOAD_TOKEN.trim()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logBuffer = StringBuilder()

    @Synchronized
    fun log(message: String) {
        logBuffer.append(message).append("\n")
    }

    fun report(context: Context, scene: String) {
        val logsToReport = synchronized(this) {
            val content = logBuffer.toString()
            logBuffer.setLength(0)
            content
        }

        if (logsToReport.isBlank()) return

        val deviceId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }

        scope.launch {
            try {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
                val body = JSONObject().apply {
                    put("device", deviceName)
                    put("device_id", deviceId ?: "")
                    put("scene", scene)
                    put("logs", logsToReport)
                }

                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val token = uploadToken
                    if (token.isNotEmpty()) {
                        setRequestProperty("X-Lobster-Token", token)
                    }
                }

                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(body.toString())
                }

                val status = connection.responseCode
                if (status in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.i(TAG, "上报成功: $response")
                } else {
                    Log.w(TAG, "上报失败: HTTP $status")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "上报异常: ${e.message}")
            }
        }
    }
}
