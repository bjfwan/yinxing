package com.yinxing.launcher.common.lobster

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LobsterDeliveryKind { REPORT, METRICS }
enum class LobsterDeliveryPriority { NORMAL, IMPORTANT, CRITICAL }

data class LobsterPendingReport(
    val id: String,
    val endpoint: String,
    val payload: String,
    val kind: LobsterDeliveryKind = LobsterDeliveryKind.REPORT,
    val priority: LobsterDeliveryPriority = LobsterDeliveryPriority.NORMAL,
    val createdAtMs: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val nextAttemptAtMs: Long = 0,
    val consentGeneration: String = "legacy"
)

object LobsterPendingReportQueue {
    const val CAPACITY = 50
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_ITEM_BYTES = 128 * 1024

    fun add(
        current: List<LobsterPendingReport>,
        report: LobsterPendingReport,
        capacity: Int,
        maxBytes: Int = MAX_BYTES
    ): List<LobsterPendingReport> {
        if (capacity <= 0) return emptyList()
        if (report.payload.toByteArray(Charsets.UTF_8).size > minOf(MAX_ITEM_BYTES, maxBytes)) return current
        val updated = current.toMutableList()
        val existing = updated.indexOfFirst { it.id == report.id }
        if (existing >= 0) updated[existing] = report else updated.add(report)
        while (updated.size > capacity || updated.sumOf { it.payload.toByteArray(Charsets.UTF_8).size.toLong() } > maxBytes) {
            val lowest = updated.minOf { it.priority.ordinal }
            updated.removeAt(updated.indexOfFirst { it.priority.ordinal == lowest })
        }
        return updated
    }

    fun remove(current: List<LobsterPendingReport>, id: String): List<LobsterPendingReport> =
        current.filterNot { it.id == id }
}

internal data class LobsterDeliveryAudit(
    val deliveryId: String,
    val result: String,
    val atMs: Long,
    val attempt: Int = 0,
    val httpStatus: Int? = null
)

internal interface LobsterDeliveryStore {
    fun read(): List<LobsterPendingReport>
    fun enqueue(report: LobsterPendingReport): Boolean
    fun update(report: LobsterPendingReport)
    fun finish(report: LobsterPendingReport, result: String, nowMs: Long, httpStatus: Int? = null)
    fun clear()
    fun cooldown(kind: LobsterDeliveryKind): Long
    fun setCooldown(kind: LobsterDeliveryKind, untilMs: Long)
}

/** 所有调用在 IO 线程。no-backup SQLite 事务提交之前，不向 HTTP 层交付条目。 */
internal class LobsterFileDeliveryStore(
    private val context: Context,
    private val databaseName: String = "lobster-diagnostics.db"
) : LobsterDeliveryStore {
    private var loaded = false
    private var reports = emptyList<LobsterPendingReport>()
    private var audit = emptyList<LobsterDeliveryAudit>()
    private var cooldowns = emptyMap<LobsterDeliveryKind, Long>()
    @Volatile var cachedCount: Int = 0
        private set

    @Synchronized
    override fun read(): List<LobsterPendingReport> {
        load()
        return reports.toList()
    }

    @Synchronized
    override fun enqueue(report: LobsterPendingReport): Boolean {
        load()
        val updated = LobsterPendingReportQueue.add(reports, report, LobsterPendingReportQueue.CAPACITY)
        val evicted = reports.filter { old -> updated.none { it.id == old.id } }
            .map { LobsterDeliveryAudit(it.id, "evicted", System.currentTimeMillis()) }
        val accepted = updated.any { it.id == report.id }
        save(updated, audit + evicted + LobsterDeliveryAudit(report.id, if (accepted) "queued" else "capacity_rejected", System.currentTimeMillis()))
        return accepted
    }

    @Synchronized
    override fun update(report: LobsterPendingReport) {
        load()
        // 发送期间已被淘汰/撤销的条目不能靠重试写回队列。
        if (reports.none { it.id == report.id }) return
        save(reports.map { if (it.id == report.id) report else it }, audit)
    }

    @Synchronized
    override fun finish(report: LobsterPendingReport, result: String, nowMs: Long, httpStatus: Int?) {
        load()
        save(reports.filterNot { it.id == report.id }, audit + LobsterDeliveryAudit(report.id, result, nowMs, report.attempts, httpStatus))
    }

    @Synchronized
    override fun clear() {
        load()
        save(emptyList(), emptyList(), emptyMap())
    }

    @Synchronized
    override fun cooldown(kind: LobsterDeliveryKind): Long {
        load()
        return cooldowns[kind] ?: 0L
    }

    @Synchronized
    override fun setCooldown(kind: LobsterDeliveryKind, untilMs: Long) {
        load()
        save(reports, audit, cooldowns + (kind to untilMs))
    }

    @Synchronized
    fun audit(): List<LobsterDeliveryAudit> { load(); return audit.toList() }

    @Synchronized
    fun migrateLegacy(context: Context, generation: String, allowed: Boolean) {
        if (LobsterAtomicStorage.read(context, KEY_STATE, databaseName) != null) return
        val prefs = context.getSharedPreferences("lobster_pending_reports", Context.MODE_PRIVATE)
        val legacy = runCatching { JSONArray(prefs.getString("reports", null) ?: "[]") }.getOrDefault(JSONArray())
        if (allowed) {
            for (index in 0 until minOf(legacy.length(), LobsterPendingReportQueue.CAPACITY)) {
                val row = legacy.optJSONObject(index) ?: continue
                val body = runCatching { JSONObject(row.optString("payload")) }.getOrNull() ?: continue
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: continue
                body.remove("physical_device_id")
                body.put("delivery_id", id)
                enqueue(LobsterPendingReport(id, row.optString("endpoint"), body.toString(),
                    priority = if (body.optString("event_level") == "error") LobsterDeliveryPriority.CRITICAL else LobsterDeliveryPriority.NORMAL,
                    consentGeneration = generation))
            }
        }
        load()
        save(reports, audit)
        check(prefs.edit().remove("reports").commit()) { "legacy queue cleanup failed" }
    }

    private companion object {
        const val KEY_STATE = "delivery_state"
    }

    @Synchronized
    private fun load() {
        if (loaded) return
        val source = LobsterAtomicStorage.read(context, KEY_STATE, databaseName)
        if (source == null) { loaded = true; return }
        check(source.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) { "delivery store exceeds limit" }
        val json = runCatching { JSONObject(source) }.getOrElse {
            LobsterAtomicStorage.write(context, KEY_STATE, null, databaseName)
            loaded = true
            return
        }
        check(json.optJSONArray("reports") != null) { "delivery queue is malformed" }
        reports = json.optJSONArray("reports")?.let { rows ->
            (0 until minOf(rows.length(), LobsterPendingReportQueue.CAPACITY)).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                runCatching {
                    LobsterPendingReport(
                        row.getString("id"), row.getString("endpoint"), row.getString("payload"),
                        LobsterDeliveryKind.valueOf(row.getString("kind")), LobsterDeliveryPriority.valueOf(row.getString("priority")),
                        row.getLong("created_at_ms"), row.getInt("attempts"), row.getLong("next_attempt_at_ms"), row.getString("consent_generation")
                    )
                }.getOrNull()
            }
        }.orEmpty()
        audit = json.optJSONArray("audit")?.let { rows ->
            (0 until minOf(rows.length(), 32)).map { index ->
                val row = rows.getJSONObject(index)
                LobsterDeliveryAudit(row.getString("id"), row.getString("result"), row.getLong("at_ms"), row.optInt("attempt"), row.optInt("http_status").takeIf { it > 0 })
            }
        }.orEmpty()
        cooldowns = LobsterDeliveryKind.entries.associateWith { json.optJSONObject("cooldowns")?.optLong(it.name) ?: 0L }
        cachedCount = reports.size
        loaded = true
    }

    private fun save(next: List<LobsterPendingReport>, nextAudit: List<LobsterDeliveryAudit>, nextCooldowns: Map<LobsterDeliveryKind, Long> = cooldowns) {
        val boundedAudit = nextAudit.takeLast(32)
        val json = JSONObject().apply {
            put("reports", JSONArray().apply {
                next.forEach { report -> put(JSONObject().apply {
                    put("id", report.id); put("endpoint", report.endpoint); put("payload", report.payload)
                    put("kind", report.kind.name); put("priority", report.priority.name)
                    put("created_at_ms", report.createdAtMs); put("attempts", report.attempts)
                    put("next_attempt_at_ms", report.nextAttemptAtMs); put("consent_generation", report.consentGeneration)
                }) }
            })
            put("audit", JSONArray().apply {
                boundedAudit.forEach { item -> put(JSONObject().apply {
                    put("id", item.deliveryId); put("result", item.result); put("at_ms", item.atMs)
                    put("attempt", item.attempt); item.httpStatus?.let { put("http_status", it) }
                }) }
            })
            put("cooldowns", JSONObject().apply { nextCooldowns.forEach { (kind, time) -> put(kind.name, time) } })
        }
        LobsterAtomicStorage.write(context, KEY_STATE, json.toString(), databaseName)
        reports = next
        cachedCount = next.size
        audit = boundedAudit
        cooldowns = nextCooldowns
    }
}
