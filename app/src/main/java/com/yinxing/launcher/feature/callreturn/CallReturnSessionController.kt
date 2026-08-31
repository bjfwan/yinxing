package com.yinxing.launcher.feature.callreturn

internal enum class CallReturnOrigin {
    SYSTEM_PHONE,
    WECHAT_VIDEO
}

internal enum class CallReturnAction {
    NONE,
    RETURN_HOME
}

internal data class CallReturnSession(
    val origin: CallReturnOrigin,
    val requestId: String,
    val armedAtMs: Long,
    val confirmed: Boolean = false,
    val userEscaped: Boolean = false
)

internal class CallReturnSessionController(
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    private var session: CallReturnSession? = null

    @Synchronized
    fun arm(
        enabled: Boolean,
        origin: CallReturnOrigin,
        requestId: String,
        nowMs: Long
    ): Boolean {
        session = if (enabled && requestId.isNotBlank()) {
            CallReturnSession(origin, requestId, nowMs)
        } else {
            null
        }
        return session != null
    }

    @Synchronized
    fun confirm(
        origin: CallReturnOrigin,
        requestId: String? = null,
        nowMs: Long
    ): Boolean {
        val current = matchingSession(origin, requestId, nowMs) ?: return false
        session = current.copy(confirmed = true)
        return true
    }

    @Synchronized
    fun markUserEscaped(nowMs: Long) {
        val current = session ?: return
        if (isExpired(current, nowMs)) {
            session = null
        } else if (current.confirmed) {
            session = current.copy(userEscaped = true)
        }
    }

    @Synchronized
    fun complete(
        origin: CallReturnOrigin,
        requestId: String? = null,
        nowMs: Long
    ): CallReturnAction {
        val current = matchingSession(origin, requestId, nowMs) ?: return CallReturnAction.NONE
        session = null
        return if (current.confirmed && !current.userEscaped) {
            CallReturnAction.RETURN_HOME
        } else {
            CallReturnAction.NONE
        }
    }

    @Synchronized
    fun cancel(origin: CallReturnOrigin, requestId: String? = null) {
        val current = session ?: return
        if (current.origin == origin && (requestId == null || current.requestId == requestId)) {
            session = null
        }
    }

    @Synchronized
    fun snapshot(): CallReturnSession? = session

    private fun matchingSession(
        origin: CallReturnOrigin,
        requestId: String?,
        nowMs: Long
    ): CallReturnSession? {
        val current = session ?: return null
        if (isExpired(current, nowMs)) {
            session = null
            return null
        }
        if (current.origin != origin) return null
        if (requestId != null && current.requestId != requestId) return null
        return current
    }

    private fun isExpired(current: CallReturnSession, nowMs: Long): Boolean =
        nowMs < current.armedAtMs || nowMs - current.armedAtMs > ttlMs

    private companion object {
        const val DEFAULT_TTL_MS = 12 * 60 * 60 * 1_000L
    }
}
