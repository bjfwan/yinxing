package com.yinxing.launcher.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingAction
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingProfileStatus
import com.yinxing.launcher.automation.wechat.teaching.WeChatTeachingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeChatTeachingSummaryFormatterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun noRuleOutcomeSaysVideoWasConfirmedButNoRuleWasLearned() {
        assertEquals(
            "视频已打通，未学到可复用规则",
            WeChatTeachingSummaryFormatter.format(
                context,
                WeChatTeachingSnapshot(
                    status = WeChatTeachingProfileStatus.VIDEO_CONFIRMED_NO_RULES,
                    learnedActions = emptySet(),
                    reliabilityScore = null
                )
            )
        )
    }

    @Test
    fun verifiedBuiltInStepsAreNotPresentedAsLearnedRules() {
        assertEquals(
            "内置可用 3 步，未新增规则",
            WeChatTeachingSummaryFormatter.format(
                context,
                WeChatTeachingSnapshot(
                    status = WeChatTeachingProfileStatus.VIDEO_CONFIRMED_NO_RULES,
                    learnedActions = emptySet(),
                    reliabilityScore = null,
                    verifiedActions = linkedSetOf(
                        WeChatTeachingAction.OPEN_MORE,
                        WeChatTeachingAction.OPEN_VIDEO_MENU,
                        WeChatTeachingAction.START_VIDEO_CALL
                    ),
                    addedActions = emptySet()
                )
            )
        )
    }

    @Test
    fun partialOutcomeNamesTheRulesThatWereSaved() {
        assertEquals(
            "已校准 2 步（内置匹配 3，设备适配 1）",
            WeChatTeachingSummaryFormatter.format(
                context,
                WeChatTeachingSnapshot(
                    status = WeChatTeachingProfileStatus.PARTIAL,
                    learnedActions = linkedSetOf(
                        WeChatTeachingAction.OPEN_MORE,
                        WeChatTeachingAction.START_VIDEO_CALL
                    ),
                    reliabilityScore = 70,
                    verifiedActions = linkedSetOf(
                        WeChatTeachingAction.OPEN_SEARCH,
                        WeChatTeachingAction.OPEN_MORE,
                        WeChatTeachingAction.START_VIDEO_CALL
                    ),
                    addedActions = setOf(WeChatTeachingAction.START_VIDEO_CALL)
                )
            )
        )
    }

    @Test
    fun unfinishedTeachingReportsInactivePendingCandidates() {
        assertEquals(
            "已保留 2 个待验证候选，不会自动执行",
            WeChatTeachingSummaryFormatter.format(
                context,
                WeChatTeachingSnapshot(
                    status = WeChatTeachingProfileStatus.CANDIDATES_PENDING,
                    learnedActions = emptySet(),
                    reliabilityScore = null,
                    pendingActions = linkedSetOf(
                        WeChatTeachingAction.OPEN_MORE,
                        WeChatTeachingAction.OPEN_VIDEO_MENU
                    )
                )
            )
        )
    }
}
