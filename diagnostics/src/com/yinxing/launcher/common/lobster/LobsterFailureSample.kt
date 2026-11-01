package com.yinxing.launcher.common.lobster

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class LobsterFailureUiState(
    val windowClass: String?,
    val semanticPage: String?,
    val route: String?,
    val resourceIds: List<String>,
    val nodeClasses: List<String>,
    val nodeCount: Int,
    val clickableCount: Int,
    val editableCount: Int,
    val maxDepth: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        safeClass(windowClass)?.let { put("window_class", it) }
        safeEnum(semanticPage)?.let { put("semantic_page", it) }
        safeEnum(route)?.let { put("route", it) }
        put("resource_ids", JSONArray(resourceIds.filter(::isSafeResourceId).distinct().sorted().take(40)))
        put("node_classes", JSONArray(nodeClasses.mapNotNull(::safeClass).distinct().sorted().take(30)))
        put("node_count", nodeCount.coerceIn(0, 500))
        put("clickable_count", clickableCount.coerceIn(0, 500))
        put("editable_count", editableCount.coerceIn(0, 100))
        put("max_depth", maxDepth.coerceIn(0, 32))
    }

    companion object {
        internal fun fromJson(json: JSONObject): LobsterFailureUiState = LobsterFailureUiState(
            windowClass = json.optString("window_class").takeIf(String::isNotEmpty),
            semanticPage = json.optString("semantic_page").takeIf(String::isNotEmpty),
            route = json.optString("route").takeIf(String::isNotEmpty),
            resourceIds = json.optJSONArray("resource_ids").toStringList(),
            nodeClasses = json.optJSONArray("node_classes").toStringList(),
            nodeCount = json.optInt("node_count"),
            clickableCount = json.optInt("clickable_count"),
            editableCount = json.optInt("editable_count"),
            maxDepth = json.optInt("max_depth"),
        )
    }
}

data class LobsterFailureSample(
    val sampleVersion: Int = 1,
    val fingerprint: String,
    val domain: String,
    val failureCode: String,
    val failedStep: String?,
    val capability: String?,
    val capabilityFailure: String?,
    val reason: String?,
    val uiState: LobsterFailureUiState,
    val targetVersionName: String? = null,
    val targetVersionCode: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sample_version", sampleVersion.coerceIn(1, 99))
        put("fingerprint", fingerprint.lowercase().takeIf(::isSafeFingerprint).orEmpty())
        put("domain", safeDomain(domain) ?: "other")
        put("failure_code", safeEnum(failureCode) ?: "UNKNOWN")
        safeEnum(failedStep)?.let { put("failed_step", it) }
        safeEnum(capability)?.let { put("capability", it) }
        safeEnum(capabilityFailure)?.let { put("capability_failure", it) }
        safeReason(reason)?.let { put("reason", it) }
        targetVersionName?.trim()?.take(40)?.takeIf(String::isNotEmpty)?.let {
            put("target_version_name", it)
        }
        targetVersionCode?.takeIf { it >= 0L }?.let { put("target_version_code", it) }
        put("ui_state", uiState.toJson())
    }

    companion object {
        internal fun fromJson(json: JSONObject): LobsterFailureSample? {
            val fingerprint = json.optString("fingerprint")
            if (!isSafeFingerprint(fingerprint)) return null
            return LobsterFailureSample(
                sampleVersion = json.optInt("sample_version", 1),
                fingerprint = fingerprint,
                domain = json.optString("domain", "other"),
                failureCode = json.optString("failure_code", "UNKNOWN"),
                failedStep = json.optString("failed_step").takeIf(String::isNotEmpty),
                capability = json.optString("capability").takeIf(String::isNotEmpty),
                capabilityFailure = json.optString("capability_failure").takeIf(String::isNotEmpty),
                reason = json.optString("reason").takeIf(String::isNotEmpty),
                uiState = LobsterFailureUiState.fromJson(json.optJSONObject("ui_state") ?: JSONObject()),
                targetVersionName = json.optString("target_version_name").takeIf(String::isNotEmpty),
                targetVersionCode = if (json.has("target_version_code") && !json.isNull("target_version_code")) {
                    json.optLong("target_version_code").takeIf { it >= 0L }
                } else {
                    null
                },
            )
        }
    }
}

data class LobsterFailureSampleRecord(
    val sample: LobsterFailureSample,
    val occurrenceCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val traceIds: List<String>,
)

object LobsterFailureSampleHistory {
    fun merge(
        existing: List<LobsterFailureSampleRecord>,
        sample: LobsterFailureSample,
        traceId: String,
        occurredAt: Long,
        maxRecords: Int = 50,
    ): List<LobsterFailureSampleRecord> {
        val previous = existing.firstOrNull { it.sample.fingerprint == sample.fingerprint }
        val traceIds = buildList {
            previous?.traceIds.orEmpty().filterTo(this) { it != traceId }
            traceId.takeIf(::isSafeTraceId)?.let(::add)
        }.takeLast(5)
        val merged = LobsterFailureSampleRecord(
            sample = sample,
            occurrenceCount = ((previous?.occurrenceCount ?: 0) + 1).coerceAtMost(1_000_000),
            firstSeenAt = previous?.firstSeenAt ?: occurredAt,
            lastSeenAt = maxOf(previous?.lastSeenAt ?: occurredAt, occurredAt),
            traceIds = traceIds,
        )
        return (existing.filterNot { it.sample.fingerprint == sample.fingerprint } + merged)
            .sortedByDescending(LobsterFailureSampleRecord::lastSeenAt)
            .take(maxRecords.coerceIn(1, 200))
    }

    fun encode(records: List<LobsterFailureSampleRecord>): String = JSONObject()
        .put("schema_version", 1)
        .put("records", JSONArray().apply {
            records.take(200).forEach { record ->
                put(JSONObject().apply {
                    put("sample", record.sample.toJson())
                    put("occurrence_count", record.occurrenceCount.coerceIn(1, 1_000_000))
                    put("first_seen_at", record.firstSeenAt.coerceAtLeast(0L))
                    put("last_seen_at", record.lastSeenAt.coerceAtLeast(0L))
                    put("trace_ids", JSONArray(record.traceIds.filter(::isSafeTraceId).takeLast(5)))
                })
            }
        })
        .toString()

    fun decode(value: String): List<LobsterFailureSampleRecord> = runCatching {
        val records = JSONObject(value).optJSONArray("records") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index) ?: continue
                val sample = record.optJSONObject("sample")?.let(LobsterFailureSample::fromJson) ?: continue
                add(
                    LobsterFailureSampleRecord(
                        sample = sample,
                        occurrenceCount = record.optInt("occurrence_count", 1).coerceIn(1, 1_000_000),
                        firstSeenAt = record.optLong("first_seen_at").coerceAtLeast(0L),
                        lastSeenAt = record.optLong("last_seen_at").coerceAtLeast(0L),
                        traceIds = record.optJSONArray("trace_ids").toStringList().filter(::isSafeTraceId).takeLast(5),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

object LobsterFailureSampleStore {
    private const val DIRECTORY = "lobster_failure_samples_v1"
    private const val FILE_NAME = "index.json"

    @Synchronized
    fun save(
        context: Context,
        sample: LobsterFailureSample,
        traceId: String,
        occurredAt: Long = System.currentTimeMillis(),
    ): LobsterFailureSampleRecord? = runCatching {
        val directory = File(context.noBackupFilesDir, DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return null
        val file = File(directory, FILE_NAME)
        val existing = if (file.exists()) LobsterFailureSampleHistory.decode(file.readText()) else emptyList()
        val merged = LobsterFailureSampleHistory.merge(existing, sample, traceId, occurredAt)
        val temporary = File(directory, "$FILE_NAME.tmp")
        temporary.writeText(LobsterFailureSampleHistory.encode(merged))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
        merged.firstOrNull { it.sample.fingerprint == sample.fingerprint }
    }.getOrNull()

    fun file(context: Context): File = File(File(context.noBackupFilesDir, DIRECTORY), FILE_NAME)
}

private val safeFingerprint = Regex("^[a-f0-9]{64}$")
private val safeEnumValue = Regex("^[A-Z][A-Z0-9_]{0,119}$")
private val safeDomainValue = Regex("^[a-z][a-z0-9_]{0,39}$")
private val safeReasonValue = Regex("^[A-Za-z0-9_.-]{1,120}$")
private val safeTraceId = Regex("^[A-Za-z0-9_-]{1,160}$")
private val safeResourceId = Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]{1,80}$")
private val safeClassName = Regex("^(?:android|androidx|com\\.tencent\\.mm)\\.[A-Za-z0-9_.$]{1,180}$")

private fun isSafeFingerprint(value: String): Boolean = safeFingerprint.matches(value)
private fun safeEnum(value: String?): String? = value?.trim()?.uppercase()?.takeIf(safeEnumValue::matches)
private fun safeDomain(value: String?): String? = value?.trim()?.lowercase()?.takeIf(safeDomainValue::matches)
private fun safeReason(value: String?): String? = value?.trim()?.takeIf(safeReasonValue::matches)
private fun isSafeTraceId(value: String): Boolean = safeTraceId.matches(value)
private fun isSafeResourceId(value: String): Boolean = safeResourceId.matches(value)
private fun safeClass(value: String?): String? = value?.trim()?.takeIf(safeClassName::matches)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotEmpty)?.let(::add)
        }
    }
}
