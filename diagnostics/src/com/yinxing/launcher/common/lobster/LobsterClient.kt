package com.yinxing.launcher.common.lobster

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.common.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class LobsterReportStatus(val wireValue: String) {
    SUCCESS("success"), ERROR("error"), REPORTED("reported")
}

object LobsterClient {
    private const val TAG = "LobsterClient"
    private const val PREFS_NAME = "lobster_client"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_DEVICE_SIGNATURE = "device_signature"
    private const val MAX_LOG_BUFFER_CHARS = 60_000
    private const val MAX_LOG_ENTRY_CHARS = 8_000

    private val endpoint: String
        get() = BuildConfig.LOBSTER_UPLOAD_URL.trim().ifBlank { "https://log.722688.xyz/api/upload" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val logBuffer = StringBuilder()
    private val sessionId = UUID.randomUUID().toString()
    private val eventSequence = LobsterEventSequence()
    private val privacyRevision = AtomicLong()
    private val runtimeLock = Any()
    @Volatile private var runtime: Runtime? = null
    @Volatile private var consentStore: LobsterDeliveryConsent? = null

    private data class Runtime(val store: LobsterFileDeliveryStore, val engine: LobsterDeliveryEngine, val triggers: LobsterDeliveryTriggers)

    /** 普通过程日志允许进程退出时丢失；关键结果必须调用 report/reportUsage。 */
    @Synchronized
    fun log(message: String) {
        logBuffer.append(LobsterLogSanitizer.sanitize(message.take(MAX_LOG_ENTRY_CHARS))).append('\n')
        if (logBuffer.length > MAX_LOG_BUFFER_CHARS) logBuffer.delete(0, logBuffer.length - MAX_LOG_BUFFER_CHARS)
    }

    fun start(context: Context) = flushPendingReports(context)

    /** UI 只读最后一次 IO 快照，不能在设置页扫描/解析队列。 */
    internal fun pendingReportCount(): Int = runtime?.store?.cachedCount ?: 0

    fun report(
        context: Context,
        scene: String,
        status: LobsterReportStatus = inferStatus(scene),
        summary: String? = null,
        details: LobsterReportDetails = LobsterReportDetails()
    ) {
        if (!shouldUploadCurrentRuntime(context)) return
        val logs = takeBufferedLogs().ifBlank { "[$scene] ${summary ?: status.wireValue}" }
        submit(context, status, details, LobsterDeliveryKind.REPORT) { app, sequence ->
            createReportBody(app, scene, status, summary, logs, details, sequence,
                LobsterEventTaxonomy.infer(scene, status, summary, details.errorCode))
        }
    }

    fun canUploadNow(context: Context): Boolean = LobsterRuntimePolicy.shouldUploadByUserPreference(context)

    fun reportUsage(context: Context, event: LobsterUsageEvent) {
        submit(context, event.status, event.details, LobsterDeliveryKind.REPORT) { app, sequence ->
            createReportBody(app, event.scene, event.status, event.summary, event.logLine, event.details, sequence,
                LobsterEventTaxonomy(event.category, event.eventType, event.action))
        }
    }

    fun reportMetrics(context: Context, metrics: List<Pair<String, Long>>, traceId: String? = null) {
        if (metrics.isEmpty()) return
        val bounded = metrics.take(100).filter { (name, value) -> name.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,119}")) && value >= 0 }
        if (bounded.isEmpty()) return
        submit(context, LobsterReportStatus.REPORTED, LobsterReportDetails(traceId = traceId), LobsterDeliveryKind.METRICS) { app, sequence ->
            baseBody(app, sequence).apply {
                put("metrics", JSONArray().apply { bounded.forEach { (name, duration) ->
                    put(JSONObject().put("name", name).put("duration_ms", duration.coerceAtMost(86_400_000)))
                } })
                traceId?.let { put("trace_id", LobsterLogSanitizer.sanitize(it.take(120))) }
            }
        }
    }

    private fun submit(
        context: Context, status: LobsterReportStatus, details: LobsterReportDetails, kind: LobsterDeliveryKind,
        body: (Context, Long) -> JSONObject
    ) {
        if (!shouldUploadCurrentRuntime(context)) return
        val app = context.applicationContext
        val revision = privacyRevision.get()
        val sequence = eventSequence.next()
        scope.launch {
            try {
                if (revision != privacyRevision.get() || !shouldUploadCurrentRuntime(app)) return@launch
                val active = ensureRuntime(app)
                val generation = consent(app).current() ?: return@launch
                val json = body(app, sequence)
                if (revision != privacyRevision.get()) return@launch
                val priority = when {
                    status == LobsterReportStatus.ERROR || details.terminal -> LobsterDeliveryPriority.CRITICAL
                    details.traceId != null || details.reportType != null -> LobsterDeliveryPriority.IMPORTANT
                    else -> LobsterDeliveryPriority.NORMAL
                }
                active.engine.enqueue(LobsterPendingReport(
                    id = json.getString("delivery_id"),
                    endpoint = if (kind == LobsterDeliveryKind.METRICS) endpoint.substringBeforeLast('/') + "/metrics" else endpoint,
                    payload = json.toString(), kind = kind, priority = priority, consentGeneration = generation
                ))
            } catch (_: Exception) {
                DebugLog.w(TAG, "诊断持久化未完成，未发送")
            }
        }
    }

    fun flushPendingReports(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val active = ensureRuntime(app)
                active.engine.trigger()
            }.onFailure { DebugLog.w(TAG, "诊断补传初始化失败") }
        }
    }

    /** Main-thread safe: revoke the in-memory send capability before the setting mutation returns. */
    fun diagnosticsPreferenceChanging(context: Context, enabled: Boolean) {
        privacyRevision.incrementAndGet()
        takeBufferedLogs()
        val permission = runCatching { consent(context.applicationContext) }.getOrNull() ?: return
        permission.invalidateImmediately()
        runtime?.engine?.consentChanged()
        if (!enabled) runtime?.triggers?.close()
    }

    fun diagnosticsPreferenceChanged(context: Context) {
        scope.launch {
            runCatching {
                val app = context.applicationContext
                val permission = consent(app)
                permission.synchronize()
                val active = ensureRuntime(app)
                if (permission.current() != null) active.triggers.start()
                active.engine.consentChanged()
                active.engine.trigger()
            }.onFailure { DebugLog.w(TAG, "诊断开关变更后补传初始化失败") }
        }
    }

    internal fun recordCrash(context: Context, snapshot: LobsterCrashSnapshot) {
        if (!shouldUploadCurrentRuntime(context)) return
        val app = context.applicationContext
        val revision = privacyRevision.get()
        // 崩溃回调不能等待应用主线程/原有协程；独立 IO 线程仅做有界持久化，不联网。
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "lobster-crash-store").apply { isDaemon = true } }
        try {
            executor.submit {
                val active = ensureRuntime(app)
                val generation = consent(app).current() ?: return@submit
                val event = snapshot.toUsageEvent()
                val body = createReportBody(app, event.scene, event.status, event.summary, event.logLine, event.details,
                    eventSequence.next(), LobsterEventTaxonomy(event.category, event.eventType, event.action))
                if (revision == privacyRevision.get() && consent(app).current() == generation) {
                    active.store.enqueue(LobsterPendingReport(body.getString("delivery_id"), endpoint, body.toString(),
                        priority = LobsterDeliveryPriority.CRITICAL, consentGeneration = generation))
                }
            }.get(2, TimeUnit.SECONDS)
        } finally { executor.shutdownNow() }
    }

    internal fun close() {
        runtime?.triggers?.close()
        runtime?.engine?.close()
        scope.cancel()
    }

    private fun consent(context: Context): LobsterDeliveryConsent = consentStore ?: synchronized(runtimeLock) {
        consentStore ?: LobsterDeliveryConsent(context.applicationContext, preference = {
            LobsterRuntimePolicy.shouldUploadByUserPreference(context)
        }).also { consentStore = it }
    }

    private fun ensureRuntime(context: Context): Runtime = runtime ?: synchronized(runtimeLock) {
        runtime ?: run {
            val permission = consent(context)
            permission.initialize()
            val store = LobsterFileDeliveryStore(context.applicationContext)
            store.migrateLegacy(context, permission.current().orEmpty(), permission.current() != null)
            var engine: LobsterDeliveryEngine? = null
            val triggers = LobsterDeliveryTriggers(context) { engine?.trigger() }
            val created = LobsterDeliveryEngine(store, LobsterHttpTransport(endpoint, { BuildConfig.LOBSTER_UPLOAD_TOKEN.trim() }),
                consent = { permission.current()?.takeIf { shouldUploadCurrentRuntime(context) } }, online = triggers::online,
                onStorageFailure = { DebugLog.w(TAG, "诊断队列存储不可用，稍后重试") })
            engine = created
            Runtime(store, created, triggers).also {
                runtime = it
                if (LobsterRuntimePolicy.shouldUpload(Build.MANUFACTURER, Build.MODEL, Build.FINGERPRINT)) triggers.start()
            }
        }
    }

    private fun inferStatus(scene: String): LobsterReportStatus = when {
        scene.contains("失败") || scene.contains("异常") || scene.contains("超时") -> LobsterReportStatus.ERROR
        scene.contains("成功") -> LobsterReportStatus.SUCCESS
        else -> LobsterReportStatus.REPORTED
    }

    @Synchronized private fun takeBufferedLogs(): String = logBuffer.toString().also { logBuffer.setLength(0) }

    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val signature = listOf(Build.MANUFACTURER, Build.MODEL, Build.DEVICE).joinToString("|") { it.trim().lowercase() }
        val resolved = LobsterDeviceIdentity.resolve(prefs.getString(KEY_INSTALL_ID, null),
            prefs.getString(KEY_DEVICE_SIGNATURE, null), signature) { UUID.randomUUID().toString() }
        check(prefs.edit().remove("physical_device_id").putString(KEY_INSTALL_ID, resolved.id)
            .putString(KEY_DEVICE_SIGNATURE, signature).commit()) { "install identity unavailable" }
        return resolved.id
    }

    private fun baseBody(context: Context, sequence: Long): JSONObject = JSONObject().apply {
        put("delivery_id", UUID.randomUUID().toString())
        put("schema_version", 5)
        put("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
        put("device_id", installId(context))
        put("session_id", sessionId)
        put("session_event_sequence", sequence)
        put("app_version", BuildConfig.VERSION_NAME)
        put("app_version_code", BuildConfig.VERSION_CODE)
        LobsterBuildIdentity.current().writeTo(this)
        put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date()))
    }

    private fun createReportBody(
        context: Context, scene: String, status: LobsterReportStatus, summary: String?, logs: String,
        details: LobsterReportDetails, sequence: Long, taxonomy: LobsterEventTaxonomy
    ): JSONObject {
        val state = runCatching { LobsterDeviceStateCollector.capture(context) }.getOrNull()
        val diagnosticLogs = listOfNotNull(logs, state?.toLogLine()?.takeIf { status == LobsterReportStatus.ERROR }).joinToString("\n")
        return baseBody(context, sequence).apply {
            put("category", taxonomy.category.wireValue)
            put("event_type", taxonomy.eventType.wireValue)
            taxonomy.action?.let { put("action", it) }
            put("scene", LobsterLogSanitizer.sanitize(scene.take(120), details.sensitiveValues))
            put("status", status.wireValue)
            put("event_level", status.wireValue)
            summary?.let { put("summary", LobsterLogSanitizer.sanitize(it.take(500), details.sensitiveValues)) }
            put("logs", LobsterLogSanitizer.sanitize(diagnosticLogs.take(MAX_LOG_BUFFER_CHARS), details.sensitiveValues))
            put("network_type", networkType(context))
            val structured = details.toJson()
            structured.keys().forEach { put(it, structured.get(it)) }
            state?.let { put("device_state", it.toJson()) }
        }
    }

    private fun networkType(context: Context): String = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "offline"
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }.getOrDefault("unknown")

    private fun shouldUploadCurrentRuntime(context: Context): Boolean = LobsterRuntimePolicy.shouldUpload(context)
}
