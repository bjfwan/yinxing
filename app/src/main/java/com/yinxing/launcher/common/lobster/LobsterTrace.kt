package com.yinxing.launcher.common.lobster

import java.util.UUID

object LobsterTrace {
    fun newId(): String = UUID.randomUUID().toString()
}

fun LobsterUsageEvent.withTrace(traceId: String): LobsterUsageEvent = copy(
    details = details.copy(traceId = traceId)
)
