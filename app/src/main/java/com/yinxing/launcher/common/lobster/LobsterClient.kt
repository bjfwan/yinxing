package com.yinxing.launcher.common.lobster

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.common.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class LobsterReportStatus(val wireValue: String) {
    SUCCESS("success"),
    ERROR("error"),
    REPORTED("reported")
}

object LobsterClient {
    private const val TAG = "LobsterClient"
    private const val MAX_LOG_BUFFER_CHARS = 60_000
    private const val MAX_LOG_ENTRY_CHARS = 8_000

    private sealed class PostJsonResult {
        data class Success(
            val httpStatus: Int,
            val response: String
        ) : PostJsonResult()

        data class Failure(
            val message: String,
            val httpStatus: Int? = null,
            val response: String? = null,
            val cause: Throwable? = null
        ) : PostJsonResult()
    }

    private val endpoint: String
        get() = BuildConfig.LOBSTER_UPLOAD_URL.trim().ifBlank {
            "https://log.likeyou.qzz.io/api/upload"
        }

    private val baseEndpoint: String
        get() = endpoint.substringBeforeLast("/")

    private val uploadToken: String
        get() = BuildConfig.LOBSTER_UPLOAD_TOKEN.trim()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logBuffer = StringBuilder()

    @Synchronized
    fun log(message: String) {
        logBuffer.append(message.take(MAX_LOG_ENTRY_CHARS)).append("\n")
        trimBufferLocked()
    }

    fun report(
        context: Context,
        scene: String,
        status: LobsterReportStatus = inferStatus(scene),
        summary: String? = null
    ) {
        val logsToReport = takeBufferedLogs()

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
                    put("status", status.wireValue)
                    summary?.trim()?.takeIf { it.isNotEmpty() }?.let { put("summary", it) }
                    put("logs", logsToReport)
                }

                val result = postJson(endpoint, body, successPrefix = "上报成功", failurePrefix = "上报失败")
                if (result !is PostJsonResult.Success) {
                    restoreBufferedLogs(logsToReport)
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "上报异常: ${e.message}", e)
                restoreBufferedLogs(logsToReport)
            }
        }
    }

    fun reportMetrics(context: Context, metrics: List<Pair<String, Long>>) {
        if (metrics.isEmpty()) return

        val deviceId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }

        scope.launch {
            try {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
                val metricsArray = JSONArray()
                for ((name, durationMs) in metrics) {
                    metricsArray.put(JSONObject().apply {
                        put("name", name)
                        put("duration_ms", durationMs)
                    })
                }

                val body = JSONObject().apply {
                    put("device", deviceName)
                    put("device_id", deviceId ?: "")
                    put("metrics", metricsArray)
                }

                val url = "$baseEndpoint/metrics"
                postJson(url, body, successPrefix = "性能指标上报成功: ${metrics.size} 条", failurePrefix = "性能指标上报失败")
            } catch (e: Exception) {
                DebugLog.e(TAG, "性能指标上报异常: ${e.message}", e)
            }
        }
    }

    private fun inferStatus(scene: String): LobsterReportStatus {
        return when {
            scene.contains("失败") || scene.contains("异常") || scene.contains("超时") -> LobsterReportStatus.ERROR
            scene.contains("成功") || scene.contains("接听") || scene.contains("挂断") -> LobsterReportStatus.SUCCESS
            else -> LobsterReportStatus.REPORTED
        }
    }

    @Synchronized
    private fun takeBufferedLogs(): String {
        val content = logBuffer.toString()
        logBuffer.setLength(0)
        return content
    }

    @Synchronized
    private fun restoreBufferedLogs(logs: String) {
        if (logs.isBlank()) {
            return
        }
        val current = logBuffer.toString()
        logBuffer.setLength(0)
        logBuffer.append(logs)
        if (!logs.endsWith("\n")) {
            logBuffer.append("\n")
        }
        logBuffer.append(current)
        trimBufferLocked()
    }

    private fun trimBufferLocked() {
        val overflow = logBuffer.length - MAX_LOG_BUFFER_CHARS
        if (overflow > 0) {
            logBuffer.delete(0, overflow)
            val firstLineBreak = logBuffer.indexOf("\n")
            if (firstLineBreak >= 0) {
                logBuffer.delete(0, firstLineBreak + 1)
            }
        }
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        successPrefix: String,
        failurePrefix: String
    ): PostJsonResult {
        var connection: HttpURLConnection? = null
        val result = try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
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
            val httpStatus = connection.responseCode
            val response = readResponse(connection, httpStatus)
            val ok = httpStatus in 200..299 && parseResponseSuccess(response)
            if (ok) {
                PostJsonResult.Success(httpStatus = httpStatus, response = response)
            } else {
                PostJsonResult.Failure(
                    message = "response rejected",
                    httpStatus = httpStatus,
                    response = response
                )
            }
        } catch (throwable: Throwable) {
            PostJsonResult.Failure(
                message = "${throwable::class.simpleName}: ${throwable.message.orEmpty()}",
                cause = throwable
            )
        } finally {
            connection?.disconnect()
        }
        logPostJsonResult(result, successPrefix, failurePrefix)
        return result
    }

    private fun logPostJsonResult(
        result: PostJsonResult,
        successPrefix: String,
        failurePrefix: String
    ) {
        when (result) {
            is PostJsonResult.Success -> {
                DebugLog.i(TAG) { "$successPrefix: ${result.response}" }
            }
            is PostJsonResult.Failure -> {
                val statusPart = result.httpStatus?.let { " HTTP $it" }.orEmpty()
                val responsePart = result.response?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                DebugLog.w(TAG, "$failurePrefix:$statusPart ${result.message}$responsePart", result.cause)
            }
        }
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun parseResponseSuccess(response: String): Boolean {
        return runCatching {
            JSONObject(response).optBoolean("success", false)
        }.getOrDefault(false)
    }
}
