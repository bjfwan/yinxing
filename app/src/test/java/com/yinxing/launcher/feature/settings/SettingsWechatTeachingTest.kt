package com.yinxing.launcher.feature.settings

import android.view.View
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.accessibility.selecttospeak.WeChatTeachingDragTargets
import com.yinxing.launcher.R
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingFingerprint
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProfile
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingReliability
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingResult
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSelector
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSemanticLabel
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStep
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsWechatTeachingTest {

    @Before
    fun clearTeachingData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("wechat_teaching_profile", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun teachingButtonIsAlsoADragSurface() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(
            R.layout.floating_wechat_teaching,
            FrameLayout(context),
            false
        )

        val dragTargets = WeChatTeachingDragTargets.collect(root)

        assertTrue(root.findViewById<View>(R.id.wechat_teaching_primary) in dragTargets)
    }

    @Test
    fun teachingOverlayMainStateIsCompactAndHasNoRepeatedTitle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(
            R.layout.floating_wechat_teaching,
            FrameLayout(context),
            false
        )
        val titleMatches = arrayListOf<View>()
        root.findViewsWithText(
            titleMatches,
            context.getString(R.string.settings_wechat_teaching_title),
            View.FIND_VIEWS_WITH_TEXT
        )

        val expectedWidth = (112 * context.resources.displayMetrics.density).toInt()
        val expectedButtonHeight = (34 * context.resources.displayMetrics.density).toInt()
        val scaledDensity = context.resources.displayMetrics.density *
            context.resources.configuration.fontScale
        val message = root.findViewById<TextView>(R.id.wechat_teaching_message)
        val primary = root.findViewById<TextView>(R.id.wechat_teaching_primary)
        assertTrue(titleMatches.isEmpty())
        assertEquals(expectedWidth, root.layoutParams.width)
        assertEquals(expectedButtonHeight, primary.layoutParams.height)
        assertEquals(11f, message.textSize / scaledDensity, 0.1f)
        assertEquals(13f, primary.textSize / scaledDensity, 0.1f)
    }

    @Test
    fun teachingOverlayLayoutDoesNotRequireMaterialTheme() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parser = context.resources.getXml(R.layout.floating_wechat_teaching)
        val tags = mutableListOf<String>()
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                tags += parser.name
            }
            parser.next()
        }
        parser.close()

        assertTrue(tags.none { it.startsWith("com.google.android.material") })
    }

    @Test
    fun contactsSettingsContainsWechatVideoTeachingEntry() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.showScreen(SettingsScreen.Contacts)

        val matches = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            matches,
            activity.getString(R.string.settings_wechat_teaching_title),
            View.FIND_VIEWS_WITH_TEXT
        )
        assertFalse(matches.isEmpty())
    }

    @Test
    fun teachingEntryOpensRuleManagementScreen() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        activity.showScreen(SettingsScreen.Contacts)
        val matches = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            matches,
            activity.getString(R.string.settings_wechat_teaching_title),
            View.FIND_VIEWS_WITH_TEXT
        )
        var clickTarget = matches.filterIsInstance<TextView>().first() as View
        while (clickTarget.id != R.id.detail_row_click_target) {
            clickTarget = clickTarget.parent as View
        }

        clickTarget.performClick()

        assertEquals(SettingsScreen.WeChatRules, activity.currentScreen)
        assertEquals(
            activity.getString(R.string.settings_wechat_rules_title),
            activity.findViewById<TextView>(R.id.settings_detail_page_title).text.toString()
        )
    }

    @Test
    fun ruleManagementShowsLearnedRuleNameAndSelector() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fingerprint = WeChatTeachingFingerprint(
            manufacturer = "vivo",
            model = "V2285A",
            androidSdk = 35,
            screenWidth = 1080,
            screenHeight = 2400,
            densityDpi = 480,
            fontScalePermille = 1000,
            localeTag = "zh-CN",
            weChatVersionName = "8.0.76",
            weChatVersionCode = 3141
        )
        val profile = WeChatTeachingProfile(
            fingerprint = fingerprint,
            steps = listOf(
                WeChatTeachingStep(
                    action = WeChatTeachingAction.OPEN_MORE,
                    windowClass = "chat",
                    expectedWindowClass = "chat-more",
                    selector = WeChatTeachingSelector(
                        resourceId = "com.tencent.mm:id/device_more",
                        nodeClass = "android.widget.ImageView",
                        semanticLabel = null,
                        clickableAncestorDepth = 0,
                        centerXRatio = 0.9f,
                        centerYRatio = 0.1f
                    )
                )
            ),
            reliabilityScore = 88,
            reliability = WeChatTeachingReliability.RELIABLE,
            createdAtEpochMs = 10_000L
        )
        WeChatTeachingStore(context).saveVideoOutcome(
            WeChatTeachingResult.Incomplete(emptySet(), profile),
            fingerprint,
            10_000L
        )
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.showScreen(SettingsScreen.WeChatRules)

        val content = activity.findViewById<View>(android.R.id.content)
        val ruleNames = arrayListOf<View>()
        val selectorDetails = arrayListOf<View>()
        content.findViewsWithText(
            ruleNames,
            activity.getString(R.string.settings_wechat_teaching_rule_more),
            View.FIND_VIEWS_WITH_TEXT
        )
        content.findViewsWithText(
            selectorDetails,
            activity.getString(R.string.settings_wechat_rules_selector_id, "device_more", 88),
            View.FIND_VIEWS_WITH_TEXT
        )
        assertFalse(ruleNames.isEmpty())
        assertFalse(selectorDetails.isEmpty())
    }

    @Test
    fun verifiedBuiltInStepsAreClearlyShownAsRecordedInsteadOfMissing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fingerprint = WeChatTeachingFingerprint(
            manufacturer = "vivo",
            model = "V2285A",
            androidSdk = 35,
            screenWidth = 1080,
            screenHeight = 2400,
            densityDpi = 480,
            fontScalePermille = 1000,
            localeTag = "zh-CN",
            weChatVersionName = "8.0.76",
            weChatVersionCode = 3141
        )
        val actions = listOf(
            WeChatTeachingAction.OPEN_MORE to WeChatTeachingSemanticLabel.MORE,
            WeChatTeachingAction.OPEN_VIDEO_MENU to WeChatTeachingSemanticLabel.VIDEO_CALL,
            WeChatTeachingAction.START_VIDEO_CALL to WeChatTeachingSemanticLabel.VIDEO_CALL
        )
        val profile = WeChatTeachingProfile(
            fingerprint = fingerprint,
            steps = actions.mapIndexed { index, (action, semanticLabel) ->
                WeChatTeachingStep(
                    action = action,
                    windowClass = "window-$index",
                    expectedWindowClass = "expected-$index",
                    selector = WeChatTeachingSelector(
                        resourceId = null,
                        nodeClass = "android.widget.TextView",
                        semanticLabel = semanticLabel,
                        clickableAncestorDepth = 0,
                        centerXRatio = null,
                        centerYRatio = null
                    )
                )
            },
            reliabilityScore = 90,
            reliability = WeChatTeachingReliability.RELIABLE,
            createdAtEpochMs = 11_000L
        )
        WeChatTeachingStore(context).saveVideoOutcome(
            WeChatTeachingResult.Complete(profile),
            fingerprint,
            11_000L
        )
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.showScreen(SettingsScreen.WeChatRules)

        val content = activity.findViewById<View>(android.R.id.content)
        val recordedTitle = arrayListOf<View>()
        val recordedSummary = arrayListOf<View>()
        content.findViewsWithText(
            recordedTitle,
            activity.getString(R.string.settings_wechat_rules_recorded_title),
            View.FIND_VIEWS_WITH_TEXT
        )
        content.findViewsWithText(
            recordedSummary,
            activity.getString(R.string.settings_wechat_rules_no_difference_summary, 3),
            View.FIND_VIEWS_WITH_TEXT
        )
        assertFalse(recordedTitle.isEmpty())
        assertFalse(recordedSummary.isEmpty())
    }
}
