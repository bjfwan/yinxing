package com.yinxing.launcher.common.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class AiProQuotaSnapshot(
    val limit: Int,
    val used: Int,
    val remaining: Int,
    val enabled: Boolean
)

data class AiProStatusSnapshot(
    val checkedAt: Long,
    val available: Boolean,
    val active: Boolean,
    val error: String,
    val callRisk: AiProQuotaSnapshot,
    val wechatStep: AiProQuotaSnapshot
) {
    companion object {
        val Empty = AiProStatusSnapshot(
            checkedAt = 0L,
            available = false,
            active = false,
            error = "",
            callRisk = AiProQuotaSnapshot(300, 0, 300, true),
            wechatStep = AiProQuotaSnapshot(80, 0, 80, true)
        )
    }
}

class AiProStatusClient(
    context: Context,
    private val aiGatewayClient: AiGatewayClient = AiGatewayClient(context)
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ai_pro_status"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_AVAILABLE = "available"
        private const val KEY_ACTIVE = "active"
        private const val KEY_ERROR = "error"
        private const val KEY_CALL_RISK_LIMIT = "call_risk_limit"
        private const val KEY_CALL_RISK_USED = "call_risk_used"
        private const val KEY_CALL_RISK_REMAINING = "call_risk_remaining"
        private const val KEY_CALL_RISK_ENABLED = "call_risk_enabled"
        private const val KEY_WECHAT_STEP_LIMIT = "wechat_step_limit"
        private const val KEY_WECHAT_STEP_USED = "wechat_step_used"
        private const val KEY_WECHAT_STEP_REMAINING = "wechat_step_remaining"
        private const val KEY_WECHAT_STEP_ENABLED = "wechat_step_enabled"
    }

    fun isConfigured(): Boolean {
        return aiGatewayClient.isConfigured()
    }

    fun cachedStatus(): AiProStatusSnapshot {
        if (!prefs.contains(KEY_CHECKED_AT)) {
            return AiProStatusSnapshot.Empty
        }
        return AiProStatusSnapshot(
            checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L),
            available = prefs.getBoolean(KEY_AVAILABLE, false),
            active = prefs.getBoolean(KEY_ACTIVE, false),
            error = prefs.getString(KEY_ERROR, "").orEmpty(),
            callRisk = AiProQuotaSnapshot(
                limit = prefs.getInt(KEY_CALL_RISK_LIMIT, 300),
                used = prefs.getInt(KEY_CALL_RISK_USED, 0),
                remaining = prefs.getInt(KEY_CALL_RISK_REMAINING, 300),
                enabled = prefs.getBoolean(KEY_CALL_RISK_ENABLED, true)
            ),
            wechatStep = AiProQuotaSnapshot(
                limit = prefs.getInt(KEY_WECHAT_STEP_LIMIT, 80),
                used = prefs.getInt(KEY_WECHAT_STEP_USED, 0),
                remaining = prefs.getInt(KEY_WECHAT_STEP_REMAINING, 80),
                enabled = prefs.getBoolean(KEY_WECHAT_STEP_ENABLED, true)
            )
        )
    }

    suspend fun refreshStatus(): AiProStatusSnapshot {
        val status = if (!isConfigured()) {
            AiProStatusSnapshot.Empty.copy(
                checkedAt = System.currentTimeMillis(),
                error = "gateway_not_configured"
            )
        } else {
            val response = withTimeoutOrNull(3500) {
                aiGatewayClient.get("/pro/status")
            }
            parseStatus(response)
        }
        saveStatus(status)
        return status
    }

    suspend fun redeemActivationCode(activationCode: String): AiProStatusSnapshot {
        val status = if (!isConfigured()) {
            AiProStatusSnapshot.Empty.copy(
                checkedAt = System.currentTimeMillis(),
                error = "gateway_not_configured"
            )
        } else {
            val response = withTimeoutOrNull(5000) {
                aiGatewayClient.post(
                    path = "/pro/redeem",
                    body = JSONObject().put("activation_code", activationCode.trim()),
                    readTimeoutMs = 5000
                )
            }
            parseStatus(response)
        }
        saveStatus(status)
        return status
    }

    private fun parseStatus(response: JSONObject?): AiProStatusSnapshot {
        if (response == null) {
            return AiProStatusSnapshot.Empty.copy(
                checkedAt = System.currentTimeMillis(),
                error = "status_unavailable"
            )
        }
        val quotas = response.optJSONObject("quotas") ?: JSONObject()
        val available = response.optBoolean("available")
        return AiProStatusSnapshot(
            checkedAt = System.currentTimeMillis(),
            available = available,
            active = available && response.optBoolean("active", true),
            error = response.optString("error"),
            callRisk = parseQuota(quotas.optJSONObject("call-risk"), 300),
            wechatStep = parseQuota(quotas.optJSONObject("wechat-step"), 80)
        )
    }

    private fun parseQuota(json: JSONObject?, fallbackLimit: Int): AiProQuotaSnapshot {
        return AiProQuotaSnapshot(
            limit = json?.optInt("limit", fallbackLimit) ?: fallbackLimit,
            used = json?.optInt("used", 0) ?: 0,
            remaining = json?.optInt("remaining", fallbackLimit) ?: fallbackLimit,
            enabled = json?.optBoolean("enabled", true) ?: true
        )
    }

    private fun saveStatus(status: AiProStatusSnapshot) {
        prefs.edit()
            .putLong(KEY_CHECKED_AT, status.checkedAt)
            .putBoolean(KEY_AVAILABLE, status.available)
            .putBoolean(KEY_ACTIVE, status.active)
            .putString(KEY_ERROR, status.error)
            .putInt(KEY_CALL_RISK_LIMIT, status.callRisk.limit)
            .putInt(KEY_CALL_RISK_USED, status.callRisk.used)
            .putInt(KEY_CALL_RISK_REMAINING, status.callRisk.remaining)
            .putBoolean(KEY_CALL_RISK_ENABLED, status.callRisk.enabled)
            .putInt(KEY_WECHAT_STEP_LIMIT, status.wechatStep.limit)
            .putInt(KEY_WECHAT_STEP_USED, status.wechatStep.used)
            .putInt(KEY_WECHAT_STEP_REMAINING, status.wechatStep.remaining)
            .putBoolean(KEY_WECHAT_STEP_ENABLED, status.wechatStep.enabled)
            .apply()
    }
}
