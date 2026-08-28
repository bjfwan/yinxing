package com.yinxing.launcher.automation.wechat.teaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatTeachingUploadFactoryTest {

    @Test
    fun uploadContainsOnlyAnonymousDeviceAndSelectorData() {
        val event = WeChatTeachingUploadFactory.create(profile())
        val rendered = listOf(
            event.summary,
            event.logLine,
            event.details.toJson().toString()
        ).joinToString("\n")

        assertEquals("upload_wechat_teaching_rule", event.action)
        assertTrue(rendered.contains("8.0.76"))
        assertTrue(rendered.contains("3141"))
        assertTrue(rendered.contains("screen=1080x2400"))
        assertTrue(rendered.contains("density=440"))
        assertTrue(rendered.contains("font_scale_permille=1000"))
        assertTrue(rendered.contains("locale=zh-CN"))
        assertTrue(rendered.contains("OPEN_SEARCH"))
        assertTrue(rendered.contains("com.tencent.mm:id/jha"))
        assertFalse(rendered.contains("wan.", ignoreCase = true))
        assertFalse(rendered.contains("今晚记得视频"))
        assertFalse(rendered.contains("13800138000"))
    }

    private fun profile() = WeChatTeachingProfile(
        fingerprint = WeChatTeachingFingerprint(
            manufacturer = "vivo",
            model = "V2285A",
            androidSdk = 36,
            screenWidth = 1080,
            screenHeight = 2400,
            densityDpi = 440,
            fontScalePermille = 1000,
            localeTag = "zh-CN",
            weChatVersionName = "8.0.76",
            weChatVersionCode = 3141
        ),
        steps = listOf(
            WeChatTeachingStep(
                action = WeChatTeachingAction.OPEN_SEARCH,
                windowClass = "com.tencent.mm.ui.LauncherUI",
                expectedWindowClass = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
                selector = WeChatTeachingSelector(
                    resourceId = "com.tencent.mm:id/jha",
                    nodeClass = "android.widget.RelativeLayout",
                    semanticLabel = WeChatTeachingSemanticLabel.SEARCH,
                    clickableAncestorDepth = 0,
                    centerXRatio = 0.8f,
                    centerYRatio = 0.1f
                )
            ),
            WeChatTeachingStep(
                action = WeChatTeachingAction.OPEN_CONTACT,
                windowClass = "今晚记得视频",
                expectedWindowClass = "wan.",
                selector = WeChatTeachingSelector(
                    resourceId = "com.tencent.mm:id/13800138000",
                    nodeClass = "wan.",
                    semanticLabel = null,
                    clickableAncestorDepth = 1,
                    centerXRatio = 0.4f,
                    centerYRatio = 0.2f
                )
            )
        ),
        reliabilityScore = 90,
        reliability = WeChatTeachingReliability.RELIABLE,
        createdAtEpochMs = 1_000L
    )
}
