package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Test

class LobsterPendingReportQueueTest {
    @Test
    fun `keeps newest reports within capacity`() {
        val reports = (1..4).map { LobsterPendingReport("id-$it", "/upload", "{$it}") }

        val queued = reports.fold(emptyList<LobsterPendingReport>()) { current, report ->
            LobsterPendingReportQueue.add(current, report, capacity = 3)
        }

        assertEquals(listOf("id-2", "id-3", "id-4"), queued.map { it.id })
    }

    @Test
    fun `replaces duplicate delivery id and removes acknowledged report`() {
        val original = LobsterPendingReport("same", "/upload", "old")
        val replacement = LobsterPendingReport("same", "/upload", "new")

        val queued = LobsterPendingReportQueue.add(listOf(original), replacement, capacity = 3)
        val remaining = LobsterPendingReportQueue.remove(queued, "same")

        assertEquals(listOf(replacement), queued)
        assertEquals(emptyList<LobsterPendingReport>(), remaining)
    }
}
