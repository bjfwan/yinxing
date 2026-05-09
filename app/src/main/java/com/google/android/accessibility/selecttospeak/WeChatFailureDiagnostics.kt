package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.DebugLog
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class WeChatFailureSnapshot(
    val step: String,
    val contactName: String,
    val startedAt: Long,
    val stepStartedAt: Long,
    val actionAttempts: Map<String, Int>,
    val stepHistory: List<String>,
    val stepDurations: Map<String, Long>,
    val lastDetectedPage: String?,
    val lastProgressAt: Long,
    val lastAnnouncedMessage: String?,
    val lastSemanticPage: String? = null,
    val taskStep: String? = null,
    val taskReason: String? = null
)

internal data class WeChatFailureReplay(
    val message: String,
    val createdAt: Long,
    val session: WeChatFailureSnapshot?,
    val root: WeChatUiSnapshot?
)

internal object WeChatFailureDiagnostics {
    private const val REPLAY_DIR = "wechat_failure_replay"
    private const val LATEST_REPLAY = "latest.json"

    fun build(
        message: String,
        session: WeChatFailureSnapshot?,
        root: AccessibilityNodeInfo?,
        service: AccessibilityService
    ): String {
        return buildString {
            append("failure=").append(message)
            if (session != null) {
                append("\nstep=").append(session.step)
                append(", contact=").append(session.contactName)
                append(", startedAt=").append(session.startedAt)
                append(", stepStartedAt=").append(session.stepStartedAt)
                append(", now=").append(System.currentTimeMillis())
                append(", actionAttempts=").append(session.actionAttempts)
                append(", stepHistory=").append(session.stepHistory)
                append(", stepDurations=").append(session.stepDurations)
                append(", lastDetectedPage=").append(session.lastDetectedPage)
                append(", lastProgressAt=").append(session.lastProgressAt)
                append(", lastAnnouncedMessage=").append(session.lastAnnouncedMessage)
                append(", lastSemanticPage=").append(session.lastSemanticPage)
                append(", taskStep=").append(session.taskStep)
                append(", taskReason=").append(session.taskReason)
            }
            append("\nroot=").append(AccessibilityUtil.summarizeNode(root))
            append("\nwindows=").append(describeWindows(service))
            append("\nnodeTree=\n").append(AccessibilityUtil.dumpTree(root))
        }
    }

    fun saveReplay(
        context: Context,
        message: String,
        session: WeChatFailureSnapshot?,
        root: WeChatUiSnapshot?,
        createdAt: Long = System.currentTimeMillis()
    ): Boolean {
        val dir = File(context.cacheDir, REPLAY_DIR)
        if (!dir.exists() && !dir.mkdirs()) return false
        val replay = WeChatFailureReplay(message, createdAt, session, root)
        return runCatching {
            File(dir, LATEST_REPLAY).writeText(encodeReplay(replay))
            true
        }.getOrDefault(false)
    }

    fun latestReplayFile(context: Context): File = File(File(context.cacheDir, REPLAY_DIR), LATEST_REPLAY)

    fun encodeReplay(replay: WeChatFailureReplay): String {
        return JSONObject()
            .put("version", 1)
            .put("message", replay.message)
            .put("createdAt", replay.createdAt)
            .put("session", replay.session?.let(::sessionToJson))
            .put("root", replay.root?.let(::snapshotToJson))
            .toString()
    }

    fun decodeReplay(value: String): WeChatFailureReplay {
        val json = JSONObject(value)
        return WeChatFailureReplay(
            message = json.optString("message"),
            createdAt = json.optLong("createdAt"),
            session = json.optJSONObject("session")?.let(::sessionFromJson),
            root = json.optJSONObject("root")?.let(::snapshotFromJson)
        )
    }

    fun describeWindows(service: AccessibilityService): String {
        val summaries = service.windows.orEmpty().mapIndexed { index, window ->
            val root = window.root
            val summary = buildString {
                append("#").append(index)
                append("(type=").append(window.type)
                append(", active=").append(window.isActive)
                append(", focused=").append(window.isFocused)
                append(", layer=").append(window.layer)
                append(", root=").append(AccessibilityUtil.summarizeNode(root))
                append(")")
            }
            AccessibilityUtil.safeRecycle(root)
            summary
        }
        return if (summaries.isEmpty()) "none" else summaries.joinToString("; ")
    }

    fun logDebugLong(tag: String, message: String) {
        message.chunked(3000).forEachIndexed { index, chunk ->
            DebugLog.d(tag) { "[$index] $chunk" }
        }
    }

    fun logErrorLong(tag: String, message: String) {
        message.chunked(3000).forEachIndexed { index, chunk ->
            DebugLog.e(tag, "[$index] $chunk")
        }
    }

    private fun sessionToJson(session: WeChatFailureSnapshot): JSONObject {
        return JSONObject()
            .put("step", session.step)
            .put("contactName", session.contactName)
            .put("startedAt", session.startedAt)
            .put("stepStartedAt", session.stepStartedAt)
            .put("actionAttempts", mapToJson(session.actionAttempts))
            .put("stepHistory", listToJson(session.stepHistory))
            .put("stepDurations", mapToJson(session.stepDurations))
            .put("lastDetectedPage", session.lastDetectedPage)
            .put("lastProgressAt", session.lastProgressAt)
            .put("lastAnnouncedMessage", session.lastAnnouncedMessage)
            .put("lastSemanticPage", session.lastSemanticPage)
            .put("taskStep", session.taskStep)
            .put("taskReason", session.taskReason)
    }

    private fun sessionFromJson(json: JSONObject): WeChatFailureSnapshot {
        return WeChatFailureSnapshot(
            step = json.optString("step"),
            contactName = json.optString("contactName"),
            startedAt = json.optLong("startedAt"),
            stepStartedAt = json.optLong("stepStartedAt"),
            actionAttempts = jsonToIntMap(json.optJSONObject("actionAttempts")),
            stepHistory = jsonToStringList(json.optJSONArray("stepHistory")),
            stepDurations = jsonToLongMap(json.optJSONObject("stepDurations")),
            lastDetectedPage = json.optString("lastDetectedPage").takeIf { it.isNotEmpty() },
            lastProgressAt = json.optLong("lastProgressAt"),
            lastAnnouncedMessage = json.optString("lastAnnouncedMessage").takeIf { it.isNotEmpty() },
            lastSemanticPage = json.optString("lastSemanticPage").takeIf { it.isNotEmpty() },
            taskStep = json.optString("taskStep").takeIf { it.isNotEmpty() },
            taskReason = json.optString("taskReason").takeIf { it.isNotEmpty() }
        )
    }

    private fun snapshotToJson(snapshot: WeChatUiSnapshot): JSONObject {
        return JSONObject()
            .put("text", snapshot.text)
            .put("contentDescription", snapshot.contentDescription)
            .put("viewIdResourceName", snapshot.viewIdResourceName)
            .put("className", snapshot.className)
            .put("clickable", snapshot.clickable)
            .put("editable", snapshot.editable)
            .put("bounds", snapshot.bounds?.let(::boundsToJson))
            .put("children", JSONArray().apply {
                snapshot.children.forEach { put(snapshotToJson(it)) }
            })
    }

    private fun snapshotFromJson(json: JSONObject): WeChatUiSnapshot {
        val children = json.optJSONArray("children")
        return WeChatUiSnapshot(
            text = json.optString("text").takeIf { it.isNotEmpty() },
            contentDescription = json.optString("contentDescription").takeIf { it.isNotEmpty() },
            viewIdResourceName = json.optString("viewIdResourceName").takeIf { it.isNotEmpty() },
            className = json.optString("className").takeIf { it.isNotEmpty() },
            clickable = json.optBoolean("clickable"),
            editable = json.optBoolean("editable"),
            bounds = json.optJSONObject("bounds")?.let(::boundsFromJson),
            children = buildList {
                if (children != null) {
                    for (index in 0 until children.length()) {
                        add(snapshotFromJson(children.getJSONObject(index)))
                    }
                }
            }
        )
    }

    private fun boundsToJson(bounds: WeChatUiBounds): JSONObject {
        return JSONObject()
            .put("left", bounds.left)
            .put("top", bounds.top)
            .put("right", bounds.right)
            .put("bottom", bounds.bottom)
    }

    private fun boundsFromJson(json: JSONObject): WeChatUiBounds {
        return WeChatUiBounds(
            left = json.optInt("left"),
            top = json.optInt("top"),
            right = json.optInt("right"),
            bottom = json.optInt("bottom")
        )
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        return JSONObject().apply {
            map.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun listToJson(list: List<String>): JSONArray {
        return JSONArray().apply {
            list.forEach(::put)
        }
    }

    private fun jsonToIntMap(json: JSONObject?): Map<String, Int> {
        if (json == null) return emptyMap()
        return json.keys().asSequence().associateWith { json.optInt(it) }
    }

    private fun jsonToLongMap(json: JSONObject?): Map<String, Long> {
        if (json == null) return emptyMap()
        return json.keys().asSequence().associateWith { json.optLong(it) }
    }

    private fun jsonToStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }
    }
}
