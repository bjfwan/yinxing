package com.yinxing.launcher.common.lobster

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LobsterPendingReport(
    val id: String,
    val endpoint: String,
    val payload: String
)

object LobsterPendingReportQueue {
    fun add(
        current: List<LobsterPendingReport>,
        report: LobsterPendingReport,
        capacity: Int
    ): List<LobsterPendingReport> {
        if (capacity <= 0) return emptyList()
        return (current.filterNot { it.id == report.id } + report).takeLast(capacity)
    }

    fun remove(current: List<LobsterPendingReport>, id: String): List<LobsterPendingReport> {
        return current.filterNot { it.id == id }
    }
}

internal object LobsterPendingReportStore {
    private const val PREFS_NAME = "lobster_pending_reports"
    private const val KEY_REPORTS = "reports"
    private const val CAPACITY = 50

    @Synchronized
    fun enqueue(context: Context, report: LobsterPendingReport, synchronous: Boolean = false) {
        val updated = LobsterPendingReportQueue.add(readLocked(context), report, CAPACITY)
        writeLocked(context, updated, synchronous)
    }

    @Synchronized
    fun read(context: Context): List<LobsterPendingReport> = readLocked(context)

    @Synchronized
    fun remove(context: Context, id: String) {
        writeLocked(
            context,
            LobsterPendingReportQueue.remove(readLocked(context), id),
            synchronous = false
        )
    }

    private fun readLocked(context: Context): List<LobsterPendingReport> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REPORTS, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val endpoint = item.optString("endpoint").takeIf(String::isNotBlank) ?: continue
                    val payload = item.optString("payload").takeIf(String::isNotBlank) ?: continue
                    add(LobsterPendingReport(id, endpoint, payload))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeLocked(
        context: Context,
        reports: List<LobsterPendingReport>,
        synchronous: Boolean
    ) {
        val array = JSONArray().apply {
            reports.forEach { report ->
                put(JSONObject().apply {
                    put("id", report.id)
                    put("endpoint", report.endpoint)
                    put("payload", report.payload)
                })
            }
        }
        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORTS, array.toString())
        if (synchronous) editor.commit() else editor.apply()
    }
}
