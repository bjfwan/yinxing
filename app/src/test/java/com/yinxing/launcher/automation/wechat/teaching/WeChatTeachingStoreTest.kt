package com.yinxing.launcher.automation.wechat.teaching

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeChatTeachingStoreTest {
    private lateinit var context: Context
    private lateinit var store: WeChatTeachingStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("wechat_teaching_profile", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = WeChatTeachingStore(context)
    }

    @Test
    fun profileRoundTripsThroughJsonAndPreferences() {
        val profile = profile()

        store.save(profile)

        assertEquals(profile, store.load())
        assertEquals(profile, WeChatTeachingProfileCodec.decode(WeChatTeachingProfileCodec.encode(profile)))
    }

    @Test
    fun profileIsOnlyCompatibleWithSameDeviceAndWechatVersion() {
        val profile = profile()
        store.save(profile)

        assertEquals(profile, store.loadCompatible(profile.fingerprint))
        assertNull(
            store.loadCompatible(
                profile.fingerprint.copy(weChatVersionCode = profile.fingerprint.weChatVersionCode + 1)
            )
        )
        assertNull(store.loadCompatible(profile.fingerprint.copy(model = "different-device")))
        assertEquals(
            WeChatTeachingProfileStatus.NEEDS_RETEACH,
            store.status(profile.fingerprint.copy(weChatVersionCode = 9999))
        )
    }

    @Test
    fun incompleteDemonstrationNeverOverwritesExistingProfile() {
        val original = profile(createdAt = 1_000L)
        store.save(original)

        val saved = store.saveIfComplete(
            WeChatTeachingResult.Incomplete(setOf(WeChatTeachingRequirement.CALL_PAGE_REACHED))
        )

        assertFalse(saved)
        assertEquals(original, store.load())
    }

    @Test
    fun completeDemonstrationIsSavedAndReportedReady() {
        val profile = profile()

        assertTrue(store.saveIfComplete(WeChatTeachingResult.Complete(profile)))
        assertEquals(WeChatTeachingProfileStatus.READY, store.status(profile.fingerprint))
    }

    @Test
    fun confirmedVideoWithNoSelectorsIsStillRemembered() {
        val fingerprint = profile().fingerprint

        val record = store.saveVideoOutcome(
            result = WeChatTeachingResult.Incomplete(
                WeChatTeachingAction.entries.mapTo(linkedSetOf()) { it.toRequirement() }
            ),
            fingerprint = fingerprint,
            createdAtEpochMs = 3_000L
        )

        assertTrue(record.videoConfirmed)
        assertTrue(record.learnedActions.isEmpty())
        assertTrue(record.verifiedActions.isEmpty())
        assertTrue(record.addedActions.isEmpty())
        assertEquals(
            WeChatTeachingProfileStatus.VIDEO_CONFIRMED_NO_RULES,
            store.status(fingerprint)
        )
        assertEquals(record, store.loadRecord())
    }

    @Test
    fun partialSelectorsAreSavedAndReportedInsteadOfDiscarded() {
        val full = profile()
        val partial = full.copy(
            steps = full.steps.takeLast(2).map { step ->
                step.copy(
                    selector = step.selector.copy(
                        resourceId = "com.tencent.mm:id/device_${step.action.name.lowercase()}",
                        semanticLabel = null
                    )
                )
            },
            reliabilityScore = 76
        )
        val result = WeChatTeachingResult.Incomplete(
            missing = setOf(
                WeChatTeachingRequirement.OPEN_SEARCH,
                WeChatTeachingRequirement.OPEN_CONTACT,
                WeChatTeachingRequirement.OPEN_MORE
            ),
            profile = partial
        )

        val record = store.saveVideoOutcome(result, full.fingerprint, 4_000L)

        assertEquals(partial.steps, store.load()?.steps)
        assertEquals(partial.steps.mapTo(linkedSetOf()) { it.action }, record.learnedActions)
        assertEquals(record.learnedActions, record.addedActions)
        assertTrue(record.verifiedActions.isEmpty())
        assertEquals(WeChatTeachingProfileStatus.PARTIAL, store.status(full.fingerprint))
    }

    @Test
    fun laterDemonstrationsMergeNewStepsWithoutErasingEarlierRules() {
        val full = profile()
        fun differenceSteps(steps: List<WeChatTeachingStep>) = steps.map { step ->
            step.copy(
                selector = step.selector.copy(
                    resourceId = "com.tencent.mm:id/device_${step.action.name.lowercase()}",
                    semanticLabel = null
                )
            )
        }
        val first = full.copy(steps = differenceSteps(full.steps.take(2)), reliabilityScore = 70)
        val second = full.copy(steps = differenceSteps(full.steps.takeLast(2)), reliabilityScore = 80)

        store.saveVideoOutcome(
            WeChatTeachingResult.Incomplete(emptySet(), first),
            full.fingerprint,
            3_000L
        )
        val record = store.saveVideoOutcome(
            WeChatTeachingResult.Incomplete(emptySet(), second),
            full.fingerprint,
            4_000L
        )

        assertEquals(4, store.load()?.steps?.size)
        assertEquals(4, record.learnedActions.size)
        assertEquals(2, record.addedActions.size)
        assertEquals(WeChatTeachingProfileStatus.PARTIAL, store.status(full.fingerprint))
    }

    @Test
    fun successfulVideoWithNoNewSelectorKeepsExistingRules() {
        val original = profile()
        store.save(original)

        val record = store.saveVideoOutcome(
            WeChatTeachingResult.Incomplete(setOf(WeChatTeachingRequirement.SELECTOR_QUALITY)),
            original.fingerprint,
            5_000L
        )

        assertEquals(original, store.load())
        assertEquals(WeChatTeachingAction.entries.toSet(), record.learnedActions)
        assertTrue(record.addedActions.isEmpty())
        assertEquals(WeChatTeachingProfileStatus.READY, store.status(original.fingerprint))
    }

    @Test
    fun builtInSelectorsAreKeptAsVerifiedCalibrationFallbacks() {
        val builtInProfile = profile().let { original ->
            original.copy(
                steps = original.steps.map { step ->
                    val builtInId = when (step.action) {
                        WeChatTeachingAction.OPEN_SEARCH -> "com.tencent.mm:id/jha"
                        WeChatTeachingAction.OPEN_CONTACT -> "com.tencent.mm:id/kbq"
                        WeChatTeachingAction.OPEN_MORE -> "com.tencent.mm:id/bjz"
                        WeChatTeachingAction.OPEN_VIDEO_MENU,
                        WeChatTeachingAction.START_VIDEO_CALL -> step.selector.resourceId
                    }
                    step.copy(selector = step.selector.copy(resourceId = builtInId))
                }
            )
        }

        val record = store.saveVideoOutcome(
            WeChatTeachingResult.Complete(builtInProfile),
            builtInProfile.fingerprint,
            6_000L
        )

        assertEquals(builtInProfile.steps, store.load()?.steps)
        assertEquals(WeChatTeachingAction.entries.toSet(), record.verifiedActions)
        assertEquals(WeChatTeachingAction.entries.toSet(), record.learnedActions)
        assertTrue(record.addedActions.isEmpty())
        assertEquals(WeChatTeachingProfileStatus.READY, store.status(builtInProfile.fingerprint))
    }

    @Test
    fun incompleteDemonstrationKeepsSelectorsPendingButNeverActivatesThem() {
        val partial = differenceProfile().copy(
            steps = differenceProfile().steps.take(2),
            reliabilityScore = 72
        )
        val result = WeChatTeachingResult.Incomplete(
            missing = setOf(WeChatTeachingRequirement.CALL_PAGE_REACHED),
            profile = partial
        )

        val pendingActions = store.savePendingCandidates(
            result = result,
            fingerprint = partial.fingerprint,
            createdAtEpochMs = 6_500L
        )

        assertEquals(partial.steps.mapTo(linkedSetOf()) { it.action }, pendingActions)
        assertNull(store.loadCompatible(partial.fingerprint))
        assertEquals(partial.steps, store.loadPendingCandidates(partial.fingerprint)?.steps)
        val snapshot = store.snapshot(partial.fingerprint)
        assertEquals(WeChatTeachingProfileStatus.CANDIDATES_PENDING, snapshot.status)
        assertEquals(pendingActions, snapshot.pendingActions)
    }

    @Test
    fun successfulVideoPromotesCurrentCalibrationAndClearsPendingCandidates() {
        val full = differenceProfile()
        val pending = full.copy(steps = full.steps.take(2), reliabilityScore = 70)
        store.savePendingCandidates(
            WeChatTeachingResult.Incomplete(
                setOf(WeChatTeachingRequirement.CALL_PAGE_REACHED),
                pending
            ),
            full.fingerprint,
            6_500L
        )

        store.saveVideoOutcome(
            WeChatTeachingResult.Complete(full),
            full.fingerprint,
            7_000L
        )

        assertEquals(full.steps, store.loadCompatible(full.fingerprint)?.steps)
        assertNull(store.loadPendingCandidates(full.fingerprint))
        assertTrue(store.snapshot(full.fingerprint).pendingActions.isEmpty())
    }

    @Test
    fun successfulPartialVideoKeepsPendingActionsNotObservedInThatRun() {
        val full = differenceProfile()
        val pending = full.copy(
            steps = full.steps.take(3),
            reliabilityScore = 70
        )
        store.savePendingCandidates(
            WeChatTeachingResult.Incomplete(
                setOf(WeChatTeachingRequirement.CALL_PAGE_REACHED),
                pending
            ),
            full.fingerprint,
            6_500L
        )
        val successfulTail = full.copy(
            steps = full.steps.takeLast(2),
            reliabilityScore = 85
        )

        store.saveVideoOutcome(
            WeChatTeachingResult.Complete(successfulTail),
            full.fingerprint,
            7_000L
        )

        assertEquals(successfulTail.steps, store.loadCompatible(full.fingerprint)?.steps)
        assertEquals(pending.steps, store.loadPendingCandidates(full.fingerprint)?.steps)
        assertEquals(
            pending.steps.mapTo(linkedSetOf()) { it.action },
            store.snapshot(full.fingerprint).pendingActions
        )
    }

    @Test
    fun legacyTeachingRecordStillLoadsWithoutPretendingOldStepsWereNew() {
        val fingerprint = profile().fingerprint
        val legacyJson = """
            {
              "schema_version":1,
              "fingerprint":{
                "manufacturer":"${fingerprint.manufacturer}",
                "model":"${fingerprint.model}",
                "android_sdk":${fingerprint.androidSdk},
                "screen_width":${fingerprint.screenWidth},
                "screen_height":${fingerprint.screenHeight},
                "density_dpi":${fingerprint.densityDpi},
                "font_scale_permille":${fingerprint.fontScalePermille},
                "locale_tag":"${fingerprint.localeTag}",
                "wechat_version_name":"${fingerprint.weChatVersionName}",
                "wechat_version_code":${fingerprint.weChatVersionCode}
              },
              "video_confirmed":true,
              "learned_actions":["OPEN_MORE"],
              "created_at_epoch_ms":1000
            }
        """.trimIndent()

        val decoded = WeChatTeachingRecordCodec.decode(legacyJson)

        assertEquals(setOf(WeChatTeachingAction.OPEN_MORE), decoded?.learnedActions)
        assertTrue(decoded?.verifiedActions.orEmpty().isEmpty())
        assertTrue(decoded?.addedActions.orEmpty().isEmpty())
    }

    @Test
    fun deletingOneLearnedRuleKeepsTheOtherRulesAndVideoResult() {
        val learned = differenceProfile()
        store.saveVideoOutcome(
            WeChatTeachingResult.Complete(learned),
            learned.fingerprint,
            7_000L
        )

        assertTrue(store.deleteLearnedAction(learned.fingerprint, WeChatTeachingAction.OPEN_MORE))

        assertNull(store.load()?.selectorFor(WeChatTeachingAction.OPEN_MORE))
        assertEquals(4, store.load()?.steps?.size)
        assertTrue(store.loadRecord()?.videoConfirmed == true)
        assertFalse(
            WeChatTeachingAction.OPEN_MORE in store.loadRecord()?.learnedActions.orEmpty()
        )
    }

    @Test
    fun clearingLearnedRulesKeepsVerifiedVideoOutcome() {
        val learned = differenceProfile()
        store.saveVideoOutcome(
            WeChatTeachingResult.Complete(learned),
            learned.fingerprint,
            8_000L
        )

        assertTrue(store.clearLearnedRules(learned.fingerprint))

        assertNull(store.load())
        assertTrue(store.loadRecord()?.videoConfirmed == true)
        assertTrue(store.loadRecord()?.learnedActions.orEmpty().isEmpty())
        assertTrue(store.loadRecord()?.addedActions.orEmpty().isEmpty())
        assertEquals(
            WeChatTeachingProfileStatus.VIDEO_CONFIRMED_NO_RULES,
            store.status(learned.fingerprint)
        )
    }

    @Test
    fun resettingTeachingRemovesRulesAndDemonstrationResult() {
        val learned = differenceProfile()
        store.saveVideoOutcome(
            WeChatTeachingResult.Complete(learned),
            learned.fingerprint,
            9_000L
        )

        assertTrue(store.resetAll())

        assertNull(store.load())
        assertNull(store.loadRecord())
        assertEquals(
            WeChatTeachingProfileStatus.NOT_TAUGHT,
            store.status(learned.fingerprint)
        )
    }

    private fun WeChatTeachingAction.toRequirement(): WeChatTeachingRequirement = when (this) {
        WeChatTeachingAction.OPEN_SEARCH -> WeChatTeachingRequirement.OPEN_SEARCH
        WeChatTeachingAction.OPEN_CONTACT -> WeChatTeachingRequirement.OPEN_CONTACT
        WeChatTeachingAction.OPEN_MORE -> WeChatTeachingRequirement.OPEN_MORE
        WeChatTeachingAction.OPEN_VIDEO_MENU -> WeChatTeachingRequirement.OPEN_VIDEO_MENU
        WeChatTeachingAction.START_VIDEO_CALL -> WeChatTeachingRequirement.START_VIDEO_CALL
    }

    private fun profile(createdAt: Long = 2_000L): WeChatTeachingProfile {
        val fingerprint = WeChatTeachingFingerprint(
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
        )
        return WeChatTeachingProfile(
            fingerprint = fingerprint,
            steps = WeChatTeachingAction.entries.mapIndexed { index, action ->
                WeChatTeachingStep(
                    action = action,
                    windowClass = "window-$index",
                    expectedWindowClass = "expected-$index",
                    selector = WeChatTeachingSelector(
                        resourceId = "com.tencent.mm:id/id$index",
                        nodeClass = "android.widget.LinearLayout",
                        semanticLabel = when (action) {
                            WeChatTeachingAction.OPEN_SEARCH -> WeChatTeachingSemanticLabel.SEARCH
                            WeChatTeachingAction.OPEN_MORE -> WeChatTeachingSemanticLabel.MORE
                            WeChatTeachingAction.OPEN_VIDEO_MENU,
                            WeChatTeachingAction.START_VIDEO_CALL -> WeChatTeachingSemanticLabel.VIDEO_CALL
                            WeChatTeachingAction.OPEN_CONTACT -> null
                        },
                        clickableAncestorDepth = index,
                        centerXRatio = 0.1f * (index + 1),
                        centerYRatio = 0.2f * (index + 1)
                    )
                )
            },
            reliabilityScore = 92,
            reliability = WeChatTeachingReliability.RELIABLE,
            createdAtEpochMs = createdAt
        )
    }

    private fun differenceProfile(): WeChatTeachingProfile = profile().let { original ->
        original.copy(
            steps = original.steps.map { step ->
                step.copy(
                    selector = step.selector.copy(
                        resourceId = "com.tencent.mm:id/device_${step.action.name.lowercase()}",
                        semanticLabel = null
                    )
                )
            }
        )
    }
}
