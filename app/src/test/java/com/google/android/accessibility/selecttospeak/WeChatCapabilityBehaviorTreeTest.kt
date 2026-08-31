package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatCapabilityBehaviorTreeTest {
    private val tree = WeChatCapabilityBehaviorTree()

    @Test
    fun `tree exposes current recent and search routes in fallback order`() {
        val root = tree.root as WeChatBehaviorNode.Sequence
        val entry = root.children.first() as WeChatBehaviorNode.Fallback

        assertEquals(
            listOf(
                WeChatRouteId.CURRENT_CONVERSATION,
                WeChatRouteId.RECENT_MESSAGES,
                WeChatRouteId.SEARCH
            ),
            entry.children.map { it.routeId }
        )
    }

    @Test
    fun `capability contract rejects unsafe page before execution`() {
        val capability = tree.capability(WeChatCapabilityId.OPEN_VIDEO_ENTRY)

        assertFalse(
            capability.precondition(
                observation(
                    page = WeChatSemanticPage.CHAT,
                    targetConversationVerified = false
                )
            )
        )
        assertTrue(
            capability.precondition(
                observation(
                    page = WeChatSemanticPage.CHAT,
                    targetConversationVerified = true
                )
            )
        )
    }

    @Test
    fun `verified target conversation skips navigation and opens video entry`() {
        val decision = tree.decide(
            state = WeChatBehaviorTreeState(),
            observation = observation(
                page = WeChatSemanticPage.CHAT,
                targetConversationVerified = true
            )
        )

        assertEquals(WeChatCapabilityId.OPEN_VIDEO_ENTRY, decision.capabilityId)
        assertEquals(WeChatRouteId.CURRENT_CONVERSATION, decision.nextState.selectedRoute)
    }

    @Test
    fun `home tries recent messages before search`() {
        val first = tree.decide(
            state = WeChatBehaviorTreeState(),
            observation = observation(WeChatSemanticPage.HOME)
        )
        val second = tree.decide(
            state = first.nextState.markFailed(
                capabilityId = WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                reason = WeChatCapabilityFailure.RECENT_TARGET_NOT_FOUND
            ),
            observation = observation(WeChatSemanticPage.HOME)
        )

        assertEquals(WeChatCapabilityId.OPEN_RECENT_CONVERSATION, first.capabilityId)
        assertEquals(WeChatCapabilityId.OPEN_SEARCH, second.capabilityId)
        assertEquals(WeChatRouteId.SEARCH, second.nextState.selectedRoute)
    }

    @Test
    fun `search route types contact then opens accepted result`() {
        val searchState = WeChatBehaviorTreeState(
            selectedRoute = WeChatRouteId.SEARCH
        )
        val type = tree.decide(
            state = searchState,
            observation = observation(
                page = WeChatSemanticPage.SEARCH,
                searchQueryVerified = false
            )
        )
        val open = tree.decide(
            state = type.nextState,
            observation = observation(
                page = WeChatSemanticPage.SEARCH,
                searchQueryVerified = true,
                contactAccepted = true
            )
        )

        assertEquals(WeChatCapabilityId.TYPE_CONTACT, type.capabilityId)
        assertEquals(WeChatCapabilityId.OPEN_SEARCH_RESULT, open.capabilityId)
    }

    @Test
    fun `unverified conversation waits for identity without reaching video capability`() {
        val decision = tree.decide(
            state = WeChatBehaviorTreeState(),
            observation = observation(
                page = WeChatSemanticPage.CHAT,
                targetConversationVerified = false
            )
        )

        assertFalse(decision.capabilityId == WeChatCapabilityId.OPEN_VIDEO_ENTRY)
        assertEquals(WeChatCapabilityId.WAIT, decision.capabilityId)
        assertEquals(WeChatCapabilityStatus.RUNNING, decision.status)
        assertEquals(WeChatCapabilityFailure.TARGET_NOT_VERIFIED, decision.failure)
    }

    @Test
    fun `video sheet selects video and confirmed call completes`() {
        val select = tree.decide(
            state = WeChatBehaviorTreeState(selectedRoute = WeChatRouteId.SEARCH),
            observation = observation(WeChatSemanticPage.VIDEO_SHEET)
        )
        val complete = tree.decide(
            state = select.nextState,
            observation = observation(
                page = WeChatSemanticPage.UNKNOWN,
                reliable = false,
                callStartedConfirmed = true
            )
        )

        assertEquals(WeChatCapabilityId.SELECT_VIDEO, select.capabilityId)
        assertTrue(complete.complete)
        assertEquals(WeChatCapabilityId.CONFIRM_CALL_STARTED, complete.capabilityId)
    }

    @Test
    fun `chat transition waits longer before recovering`() {
        assertEquals(
            WeChatLowConfidenceAction.WAIT,
            WeChatLowConfidenceRecoveryPolicy.decide(
                currentClass = WeChatClassNames.CHATTING_UI,
                observeAttempt = WeChatLowConfidenceRecoveryPolicy.MAX_CONVERSATION_SETTLE_OBSERVATIONS,
                conversationRecoveries = 0
            )
        )
    }

    @Test
    fun `first exhausted chat transition may recover through another route`() {
        assertEquals(
            WeChatLowConfidenceAction.RECOVER_HOME,
            WeChatLowConfidenceRecoveryPolicy.decide(
                currentClass = WeChatClassNames.CHATTING_UI,
                observeAttempt = WeChatLowConfidenceRecoveryPolicy.MAX_CONVERSATION_SETTLE_OBSERVATIONS + 1,
                conversationRecoveries = 0
            )
        )
    }

    @Test
    fun `second exhausted chat transition fails safely instead of looping`() {
        assertEquals(
            WeChatLowConfidenceAction.FAIL_SAFE,
            WeChatLowConfidenceRecoveryPolicy.decide(
                currentClass = WeChatClassNames.CHATTING_UI,
                observeAttempt = WeChatLowConfidenceRecoveryPolicy.MAX_CONVERSATION_SETTLE_OBSERVATIONS + 1,
                conversationRecoveries = 1
            )
        )
    }

    @Test
    fun `generic unknown page retains short observation budget`() {
        assertEquals(
            WeChatLowConfidenceAction.RECOVER_HOME,
            WeChatLowConfidenceRecoveryPolicy.decide(
                currentClass = "android.widget.FrameLayout",
                observeAttempt = WeChatLowConfidenceRecoveryPolicy.MAX_GENERIC_OBSERVATIONS + 1,
                conversationRecoveries = 0
            )
        )
    }

    @Test
    fun `legacy exact title can verify a known conversation page`() {
        assertTrue(
            WeChatConversationVerificationPolicy.isVerified(
                page = WeChatSemanticPage.CHAT,
                semanticTitleVerified = false,
                legacyExactTitleVerified = true
            )
        )
    }

    @Test
    fun `chat activity alone never verifies target identity`() {
        assertFalse(
            WeChatConversationVerificationPolicy.isVerified(
                page = WeChatSemanticPage.CHAT,
                semanticTitleVerified = false,
                legacyExactTitleVerified = false
            )
        )
    }

    private fun observation(
        page: WeChatSemanticPage,
        reliable: Boolean = true,
        targetConversationVerified: Boolean = false,
        searchQueryVerified: Boolean = false,
        contactAccepted: Boolean = false,
        callStartedConfirmed: Boolean = false
    ) = WeChatCapabilityObservation(
        page = WeChatSemanticPageResult(
            page = page,
            confidence = if (reliable) 90 else 20,
            evidence = listOf(page.name.lowercase())
        ),
        targetConversationVerified = targetConversationVerified,
        searchQueryVerified = searchQueryVerified,
        contactAccepted = contactAccepted,
        callStartedConfirmed = callStartedConfirmed
    )
}
