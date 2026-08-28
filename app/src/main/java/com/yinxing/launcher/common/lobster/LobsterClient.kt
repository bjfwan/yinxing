package com.yinxing.launcher.common.lobster

import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class LobsterReportStatus(val wireValue: String) {
    SUCCESS("success"),
    ERROR("error"),
    REPORTED("reported")
}

object LobsterClient {
    private const val TAG = "LobsterClient"
    private const val PREFS_NAME = "lobster_client"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_DEVICE_SIGNATURE = "device_signature"
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
    private val sessionId = UUID.randomUUID().toString()

    @Synchronized
    fun log(message: String) {
        logBuffer.append(message.take(MAX_LOG_ENTRY_CHARS)).append("\n")
        trimBufferLocked()
    }

    fun report(
        context: Context,
        scene: String,
        status: LobsterReportStatus = inferStatus(scene),
        summary: String? = null,
        details: LobsterReportDetails = LobsterReportDetails()
    ) {
        if (!shouldUploadCurrentRuntime()) return
        val logsToReport = takeBufferedLogs()

        if (logsToReport.isBlank()) return

        val deviceId = installId(context)

        scope.launch {
            var queued = false
            try {
                val body = createReportBody(
                    context = context,
                    deviceId = deviceId,
                    scene = scene,
                    status = status,
                    summary = summary,
                    logs = logsToReport,
                    details = details,
                    taxonomy = LobsterEventTaxonomy.infer(scene, status, summary, details.errorCode)
                )
                val pending = LobsterPendingReport(
                    id = body.getString("delivery_id"),
                    endpoint = endpoint,
                    payload = body.toString()
                )
                LobsterPendingReportStore.enqueue(context.applicationContext, pending)
                queued = true
                val result = postJson(endpoint, body, successPrefix = "上报成功", failurePrefix = "上报失败")
                if (result is PostJsonResult.Success) {
                    LobsterPendingReportStore.remove(context.applicationContext, pending.id)
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "上报异常: ${e.message}", e)
                if (!queued) restoreBufferedLogs(logsToReport)
            }
        }
    }

    fun reportUsage(context: Context, event: LobsterUsageEvent) {
        if (!shouldUploadCurrentRuntime()) return
        val appContext = context.applicationContext
        val deviceId = installId(appContext)

        scope.launch {
            try {
                val body = createReportBody(
                    context = appContext,
                    deviceId = deviceId,
                    scene = event.scene,
                    status = event.status,
                    summary = event.summary,
                    logs = event.logLine,
                    details = event.details,
                    taxonomy = LobsterEventTaxonomy(event.category, event.eventType, event.action)
                )
                val pending = LobsterPendingReport(
                    id = body.getString("delivery_id"),
                    endpoint = endpoint,
                    payload = body.toString()
                )
                LobsterPendingReportStore.enqueue(appContext, pending)
                val result = postJson(
                    endpoint,
                    body,
                    successPrefix = "使用事件上报成功",
                    failurePrefix = "使用事件上报失败"
                )
                if (result is PostJsonResult.Success) {
                    LobsterPendingReportStore.remove(appContext, pending.id)
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "使用事件上报异常: ${e.message}", e)
            }
        }
    }

    fun flushPendingReports(context: Context) {
        if (!shouldUploadCurrentRuntime()) return
        val appContext = context.applicationContext
        scope.launch {
            LobsterPendingReportStore.read(appContext).forEach { pending ->
                val body = runCatching { JSONObject(pending.payload) }.getOrNull()
                if (body == null) {
                    LobsterPendingReportStore.remove(appContext, pending.id)
                    return@forEach
                }
                val result = postJson(
                    pending.endpoint,
                    body,
                    successPrefix = "待发送日志补传成功",
                    failurePrefix = "待发送日志补传失败"
                )
                if (result is PostJsonResult.Success) {
                    LobsterPendingReportStore.remove(appContext, pending.id)
                }
            }
        }
    }

    internal fun recordCrash(context: Context, snapshot: LobsterCrashSnapshot) {
        if (!shouldUploadCurrentRuntime()) return
        val appContext = context.applicationContext
        val event = snapshot.toUsageEvent()
        val body = createReportBody(
            context = appContext,
            deviceId = installId(appContext),
            scene = event.scene,
            status = event.status,
            summary = event.summary,
            logs = event.logLine,
            details = event.details,
            taxonomy = LobsterEventTaxonomy(event.category, event.eventType, event.action)
        )
        LobsterPendingReportStore.enqueue(
            appContext,
            LobsterPendingReport(
                id = body.getString("delivery_id"),
                endpoint = endpoint,
                payload = body.toString()
            ),
            synchronous = true
        )
    }

    fun reportMetrics(context: Context, metrics: List<Pair<String, Long>>, traceId: String? = null) {
        if (metrics.isEmpty() || !shouldUploadCurrentRuntime()) return

        val deviceId = installId(context)

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
                    put("device_id", deviceId)
                    put("metrics", metricsArray)
                    put("session_id", sessionId)
                    put("app_version", BuildConfig.VERSION_NAME)
                    put("app_version_code", BuildConfig.VERSION_CODE)
                    traceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("trace_id", it.take(120)) }
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

    @Synchronized
    private fun installId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val signature = listOf(Build.MANUFACTURER, Build.MODEL, Build.DEVICE)
            .joinToString("|") { it.trim().lowercase() }
        val resolved = LobsterDeviceIdentity.resolve(
            storedId = prefs.getString(KEY_INSTALL_ID, null),
            storedDeviceSignature = prefs.getString(KEY_DEVICE_SIGNATURE, null),
            currentDeviceSignature = signature,
            createId = { UUID.randomUUID().toString() }
        )
        prefs.edit()
            .putString(KEY_INSTALL_ID, resolved.id)
            .putString(KEY_DEVICE_SIGNATURE, signature)
            .apply()
        return resolved.id
    }

    private fun currentIsoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private fun networkType(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unknown"
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    private fun createReportBody(
        context: Context,
        deviceId: String,
        scene: String,
        status: LobsterReportStatus,
        summary: String?,
        logs: String,
        details: LobsterReportDetails,
        taxonomy: LobsterEventTaxonomy
    ): JSONObject {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        val deviceState = runCatching { LobsterDeviceStateCollector.capture(context) }.getOrNull()
        val diagnosticLogs = listOfNotNull(
            logs,
            deviceState?.toLogLine()?.takeIf { status == LobsterReportStatus.ERROR }
        )
            .filter(String::isNotBlank)
            .joinToString("\n")
        return JSONObject().apply {
            put("delivery_id", UUID.randomUUID().toString())
            put("schema_version", 4)
            put("category", taxonomy.category.wireValue)
            put("event_type", taxonomy.eventType.wireValue)
            taxonomy.action?.let { put("action", it) }
            put("device", deviceName)
            put("device_id", deviceId)
            put("scene", scene)
            put("status", status.wireValue)
            summary?.trim()?.takeIf { it.isNotEmpty() }?.let {
                put("summary", LobsterLogSanitizer.sanitize(it, details.sensitiveValues))
            }
            put("logs", LobsterLogSanitizer.sanitize(diagnosticLogs, details.sensitiveValues))
            put("event_level", status.wireValue)
            put("session_id", sessionId)
            put("app_version", BuildConfig.VERSION_NAME)
            put("app_version_code", BuildConfig.VERSION_CODE)
            put("created_at", currentIsoTimestamp())
            put("network_type", networkType(context))
            val structured = details.toJson()
            structured.keys().forEach { key -> put(key, structured.get(key)) }
            deviceState?.let { put("device_state", it.toJson()) }
        }
    }

    private fun shouldUploadCurrentRuntime(): Boolean {
        return LobsterRuntimePolicy.shouldUpload(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            fingerprint = Build.FINGERPRINT
        )
    }
}
