package com.google.android.accessibility.selecttospeak

import android.content.Context
import android.util.Log
import com.yinxing.launcher.common.ai.AiGatewayClient
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal enum class WeChatStepAssistAction(val value: String) {
    Wait("wait"),
    TapSearch("tap_search"),
    InputContact("input_contact"),
    TapContact("tap_contact"),
    TapVideoCall("tap_video_call"),
    TapVideoOption("tap_video_option"),
    Fail("fail");

    companion object {
        fun from(value: String): WeChatStepAssistAction {
            return entries.firstOrNull { it.value == value } ?: Fail
        }
    }
}

internal data class WeChatStepAssistDecision(
    val page: String,
    val action: WeChatStepAssistAction,
    val targetText: String,
    val confidence: Float,
    val reason: String
)

internal class WeChatStepAssistClient(
    context: Context,
    private val aiGatewayClient: AiGatewayClient = AiGatewayClient(context)
) {
    companion object {
        private const val TAG = "WeChatStepAssist"
    }

    fun isConfigured(): Boolean {
        return aiGatewayClient.isConfigured()
    }

    suspend fun decide(
        step: String,
        currentClass: String?,
        targetAlias: String,
        failureReason: String,
        snapshot: WeChatUiSnapshot,
        mode: String = "resolve"
    ): WeChatStepAssistDecision? {
        if (!isConfigured()) {
            Log.w(TAG, "skip step=$step reason=not_configured")
            return null
        }
        val startedAt = System.currentTimeMillis()
        val nodes = snapshot.toJsonNodes()
        val cacheOnly = mode == "cache_only"
        val body = JSONObject().apply {
            put("mode", if (cacheOnly) "cache_only" else "resolve")
            put("step", step)
            put("current_class", currentClass.orEmpty())
            put("target_alias", targetAlias)
            put("failure_reason", failureReason)
            put("nodes", nodes)
        }
        val timeoutMs = if (cacheOnly) 2200L else 10000L
        val connectTimeoutMs = if (cacheOnly) 1200 else 3000
        val readTimeoutMs = if (cacheOnly) 1800 else 9000
        Log.d(TAG, "start mode=${if (cacheOnly) "cache_only" else "resolve"} step=$step failure=$failureReason class=${currentClass.orEmpty()} nodes=${nodes.length()}")
        val response = withTimeoutOrNull(timeoutMs) {
            aiGatewayClient.post(
                path = "/ai/wechat-step",
                body = body,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
        } ?: run {
            Log.w(TAG, "timeout mode=${if (cacheOnly) "cache_only" else "resolve"} step=$step elapsed=${System.currentTimeMillis() - startedAt}ms")
            return null
        }
        if (!response.optBoolean("available")) {
            Log.w(TAG, "unavailable mode=${if (cacheOnly) "cache_only" else "resolve"} step=$step error=${response.optString("error")} elapsed=${System.currentTimeMillis() - startedAt}ms")
            return null
        }
        val decision = WeChatStepAssistDecision(
            page = response.optString("page", "unknown"),
            action = WeChatStepAssistAction.from(response.optString("next_action")),
            targetText = response.optString("target_text").trim(),
            confidence = response.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            reason = response.optString("reason").trim()
        )
        Log.d(TAG, "done mode=${if (cacheOnly) "cache_only" else "resolve"} step=$step action=${decision.action.value} page=${decision.page} confidence=${decision.confidence} cache=${response.optBoolean("cache_hit")} model=${response.optBoolean("model_called")} elapsed=${System.currentTimeMillis() - startedAt}ms")
        return decision
    }

    private fun WeChatUiSnapshot.toJsonNodes(): JSONArray {
        val nodes = JSONArray()
        flatten()
            .filter { it.hasUsefulSignal() }
            .take(80)
            .forEach { node ->
                nodes.put(JSONObject().apply {
                    put("text", node.text.orEmpty().take(40))
                    put("content_description", node.contentDescription.orEmpty().take(40))
                    put("view_id", node.viewIdResourceName.orEmpty().take(80))
                    put("class_name", node.className.orEmpty().take(80))
                    put("clickable", node.clickable)
                    put("editable", node.editable)
                })
            }
        return nodes
    }

    private fun WeChatUiSnapshot.hasUsefulSignal(): Boolean {
        return !text.isNullOrBlank() ||
            !contentDescription.isNullOrBlank() ||
            !viewIdResourceName.isNullOrBlank() ||
            editable ||
            clickable
    }
}
