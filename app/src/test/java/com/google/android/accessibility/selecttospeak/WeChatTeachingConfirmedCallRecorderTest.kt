package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservation
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingObservationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingConfirmedCallRecorderTest {

    @Test
    fun confirmedVideoAddsTheWindowEvidenceMissingOnSomeDevices() {
        val observations = mutableListOf<WeChatTeachingObservation>()

        val appended = WeChatTeachingConfirmedCallRecorder.appendIfMissing(
            observations = observations,
            elapsedMs = 1_000L,
            maxSize = 100
        )

        assertTrue(appended)
        assertEquals(WeChatTeachingObservationKind.WINDOW, observations.single().kind)
        assertEquals(
            "com.tencent.mm.plugin.voip.ui.VideoActivity",
            observations.single().windowClass
        )
    }
}
