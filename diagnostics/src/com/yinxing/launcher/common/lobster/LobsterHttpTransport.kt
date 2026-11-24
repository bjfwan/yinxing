package com.yinxing.launcher.common.lobster

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class LobsterHttpTransport(
    private val reportEndpoint: String,
    private val token: () -> String,
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
    private val clock: () -> Long = System::currentTimeMillis
) : LobsterDeliveryTransport {
    private val metricsEndpoint = reportEndpoint.substringBeforeLast('/') + "/metrics"
    @Volatile private var active: HttpURLConnection? = null

    override fun cancel() { active?.disconnect() }

    override fun send(report: LobsterPendingReport, permitted: () -> Boolean): LobsterDeliveryResponse {
        val expected = if (report.kind == LobsterDeliveryKind.METRICS) metricsEndpoint else reportEndpoint
        if (report.endpoint != expected || !expected.startsWith("https://")) return LobsterDeliveryResponse(httpStatus = 400)
        if (!permitted()) return LobsterDeliveryResponse()
        // 旧 metrics 接口没有幂等。只探测能力，不向它试发一条可能被重复计数的 POST。
        if (report.kind == LobsterDeliveryKind.METRICS && !supportsMetricDelivery(permitted)) {
            return LobsterDeliveryResponse(capabilityUnavailable = true)
        }
        var connection: HttpURLConnection? = null
        return try {
            val body = JSONObject(report.payload)
            body.remove("physical_device_id")
            body.put("delivery_id", report.id)
            if (!permitted()) return LobsterDeliveryResponse()
            connection = configure(open(expected), "POST")
            active = connection
            if (!permitted()) return LobsterDeliveryResponse()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Idempotency-Key", report.id)
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            if (!permitted()) return LobsterDeliveryResponse()
            connection.outputStream.use { if (permitted()) it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { reader ->
                val text = StringBuilder()
                val chars = CharArray(4096)
                while (text.length <= 262_144) {
                    val length = reader.read(chars)
                    if (length < 0) break
                    text.append(chars, 0, length)
                }
                text.toString()
            }.orEmpty()
            val json = runCatching { JSONObject(response) }.getOrNull()
            val ack = json?.optJSONObject("data")?.optString("delivery_id") ?: connection.getHeaderField("X-Lobster-Delivery-Id")
            val accepted = status in 200..299 && (status == 204 || json?.optBoolean("success") == true) &&
                (report.kind != LobsterDeliveryKind.METRICS || ack == report.id)
            LobsterDeliveryResponse(status, accepted, retryAfterMs(connection.getHeaderField("Retry-After"), clock()))
        } catch (_: Exception) {
            LobsterDeliveryResponse()
        } finally {
            connection?.disconnect()
            if (active === connection) active = null
        }
    }

    private fun supportsMetricDelivery(permitted: () -> Boolean): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            if (!permitted()) return false
            connection = configure(open(metricsEndpoint), "OPTIONS")
            active = connection
            if (!permitted()) return false
            connection.responseCode in 200..299 &&
                connection.getHeaderField("X-Lobster-Idempotency") == "delivery-id-v1"
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
            if (active === connection) active = null
        }
    }

    private fun configure(connection: HttpURLConnection, method: String): HttpURLConnection = connection.apply {
        requestMethod = method
        connectTimeout = 5000
        readTimeout = 15000
        instanceFollowRedirects = false
        useCaches = false
        token().takeIf(String::isNotEmpty)?.let { setRequestProperty("X-Lobster-Token", it) }
    }

    companion object {
        internal fun retryAfterMs(value: String?, nowMs: Long): Long {
            val seconds = value?.trim()?.toLongOrNull()
            if (seconds != null) return seconds.coerceIn(0, 86_400) * 1000
            return runCatching {
                val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(value.orEmpty())
                ((date?.time ?: nowMs) - nowMs).coerceIn(0, LobsterRetryPolicy.MAX_RETRY_AFTER_MS)
            }.getOrDefault(0)
        }
    }
}
