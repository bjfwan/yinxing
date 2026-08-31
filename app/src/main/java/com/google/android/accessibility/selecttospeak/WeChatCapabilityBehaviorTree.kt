package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames

/** Atomic, loggable actions used by the WeChat video behavior tree. */
internal enum class WeChatCapabilityId {
    LAUNCH_WECHAT,
    VERIFY_TARGET_CONVERSATION,
    OPEN_RECENT_CONVERSATION,
    OPEN_SEARCH,
    TYPE_CONTACT,
    OPEN_SEARCH_RESULT,
    OPEN_VIDEO_ENTRY,
    SELECT_VIDEO,
    CONFIRM_CALL_STARTED,
    RECOVER_HOME,
    WAIT
}

internal enum class WeChatRouteId {
    CURRENT_CONVERSATION,
    RECENT_MESSAGES,
    SEARCH
}

internal enum class WeChatCapabilityStatus {
    READY,
    RUNNING,
    SUCCEEDED,
    FAILED
}

internal enum class WeChatCapabilityFailure {
    LOW_PAGE_CONFIDENCE,
    TARGET_NOT_VERIFIED,
    RECENT_TARGET_NOT_FOUND,
    SEARCH_RESULT_NOT_FOUND,
    PRECONDITION_NOT_MET,
    ACTION_FAILED,
    CALL_NOT_CONFIRMED
}

internal enum class WeChatLowConfidenceAction {
    WAIT,
    RECOVER_HOME,
    FAIL_SAFE
}

internal object WeChatLowConfidenceRecoveryPolicy {
    const val MAX_GENERIC_OBSERVATIONS = 2
    const val MAX_CONVERSATION_SETTLE_OBSERVATIONS = 10

    fun decide(
        currentClass: String?,
        observeAttempt: Int,
        conversationRecoveries: Int
    ): WeChatLowConfidenceAction {
        val conversationPage = isConversationClass(currentClass)
        val maxObservations = if (conversationPage) {
            MAX_CONVERSATION_SETTLE_OBSERVATIONS
        } else {
            MAX_GENERIC_OBSERVATIONS
        }
        if (observeAttempt <= maxObservations) {
            return WeChatLowConfidenceAction.WAIT
        }
        return if (conversationPage && conversationRecoveries > 0) {
            WeChatLowConfidenceAction.FAIL_SAFE
        } else {
            WeChatLowConfidenceAction.RECOVER_HOME
        }
    }

    fun isConversationClass(currentClass: String?): Boolean =
        currentClass == WeChatClassNames.CHATTING_UI ||
            currentClass == WeChatClassNames.CONTACT_INFO
}

internal object WeChatConversationVerificationPolicy {
    fun isVerified(
        page: WeChatSemanticPage,
        semanticTitleVerified: Boolean,
        legacyExactTitleVerified: Boolean
    ): Boolean =
        (page == WeChatSemanticPage.CHAT || page == WeChatSemanticPage.CONTACT_DETAIL) &&
            (semanticTitleVerified || legacyExactTitleVerified)
}

internal data class WeChatCapabilityResult(
    val capabilityId: WeChatCapabilityId,
    val status: WeChatCapabilityStatus,
    val failure: WeChatCapabilityFailure? = null
)

/**
 * Stable boundary between route planning and Android accessibility side effects.
 * Implementations receive semantic state only; contact text and arbitrary page text are excluded.
 */
internal interface WeChatCapability {
    val id: WeChatCapabilityId

    fun precondition(observation: WeChatCapabilityObservation): Boolean

    fun observeResult(observation: WeChatCapabilityObservation): WeChatCapabilityResult
}

private class SemanticWeChatCapability(
    override val id: WeChatCapabilityId,
    private val isReady: (WeChatCapabilityObservation) -> Boolean,
    private val isComplete: (WeChatCapabilityObservation) -> Boolean
) : WeChatCapability {
    override fun precondition(observation: WeChatCapabilityObservation): Boolean =
        isReady(observation)

    override fun observeResult(
        observation: WeChatCapabilityObservation
    ): WeChatCapabilityResult = when {
        isComplete(observation) -> WeChatCapabilityResult(
            capabilityId = id,
            status = WeChatCapabilityStatus.SUCCEEDED
        )
        isReady(observation) -> WeChatCapabilityResult(
            capabilityId = id,
            status = WeChatCapabilityStatus.READY
        )
        else -> WeChatCapabilityResult(
            capabilityId = id,
            status = WeChatCapabilityStatus.FAILED,
            failure = WeChatCapabilityFailure.PRECONDITION_NOT_MET
        )
    }
}

internal data class WeChatCapabilityObservation(
    val page: WeChatSemanticPageResult,
    val targetConversationVerified: Boolean = false,
    val searchQueryVerified: Boolean = false,
    val contactAccepted: Boolean = false,
    val callStartedConfirmed: Boolean = false
)

internal data class WeChatBehaviorTreeState(
    val selectedRoute: WeChatRouteId? = null,
    val completedCapabilities: Set<WeChatCapabilityId> = emptySet(),
    val failedCapabilities: Map<WeChatCapabilityId, WeChatCapabilityFailure> = emptyMap()
) {
    fun markFailed(
        capabilityId: WeChatCapabilityId,
        reason: WeChatCapabilityFailure
    ): WeChatBehaviorTreeState = copy(
        failedCapabilities = failedCapabilities + (capabilityId to reason)
    )

    fun markSucceeded(capabilityId: WeChatCapabilityId): WeChatBehaviorTreeState = copy(
        completedCapabilities = completedCapabilities + capabilityId,
        failedCapabilities = failedCapabilities - capabilityId
    )
}

internal data class WeChatBehaviorDecision(
    val nextState: WeChatBehaviorTreeState,
    val capabilityId: WeChatCapabilityId,
    val reason: String,
    val status: WeChatCapabilityStatus = WeChatCapabilityStatus.READY,
    val failure: WeChatCapabilityFailure? = null,
    val complete: Boolean = false
)

internal sealed interface WeChatBehaviorNode {
    data class Sequence(val children: List<WeChatBehaviorNode>) : WeChatBehaviorNode

    data class Fallback(val children: List<Route>) : WeChatBehaviorNode

    data class Route(
        val routeId: WeChatRouteId,
        val children: List<Capability>
    ) : WeChatBehaviorNode

    data class Capability(val capabilityId: WeChatCapabilityId) : WeChatBehaviorNode
}

internal class WeChatCapabilityBehaviorTree {
    private val capabilities: Map<WeChatCapabilityId, WeChatCapability> =
        WeChatCapabilityId.entries.associateWith(::createCapability)

    val root: WeChatBehaviorNode = WeChatBehaviorNode.Sequence(
        listOf(
            WeChatBehaviorNode.Fallback(
                listOf(
                    WeChatBehaviorNode.Route(
                        routeId = WeChatRouteId.CURRENT_CONVERSATION,
                        children = listOf(
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.VERIFY_TARGET_CONVERSATION)
                        )
                    ),
                    WeChatBehaviorNode.Route(
                        routeId = WeChatRouteId.RECENT_MESSAGES,
                        children = listOf(
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.OPEN_RECENT_CONVERSATION),
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.VERIFY_TARGET_CONVERSATION)
                        )
                    ),
                    WeChatBehaviorNode.Route(
                        routeId = WeChatRouteId.SEARCH,
                        children = listOf(
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.OPEN_SEARCH),
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.TYPE_CONTACT),
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.OPEN_SEARCH_RESULT),
                            WeChatBehaviorNode.Capability(WeChatCapabilityId.VERIFY_TARGET_CONVERSATION)
                        )
                    )
                )
            ),
            WeChatBehaviorNode.Capability(WeChatCapabilityId.OPEN_VIDEO_ENTRY),
            WeChatBehaviorNode.Capability(WeChatCapabilityId.SELECT_VIDEO),
            WeChatBehaviorNode.Capability(WeChatCapabilityId.CONFIRM_CALL_STARTED)
        )
    )

    fun capability(id: WeChatCapabilityId): WeChatCapability = capabilities.getValue(id)

    fun decide(
        state: WeChatBehaviorTreeState,
        observation: WeChatCapabilityObservation
    ): WeChatBehaviorDecision {
        if (observation.callStartedConfirmed) {
            return WeChatBehaviorDecision(
                nextState = state.markSucceeded(WeChatCapabilityId.CONFIRM_CALL_STARTED),
                capabilityId = WeChatCapabilityId.CONFIRM_CALL_STARTED,
                reason = "call_started_confirmed",
                status = WeChatCapabilityStatus.SUCCEEDED,
                complete = true
            )
        }

        if (observation.page.page == WeChatSemanticPage.VIDEO_SHEET) {
            return ready(
                state = state.markSucceeded(WeChatCapabilityId.OPEN_VIDEO_ENTRY),
                capabilityId = WeChatCapabilityId.SELECT_VIDEO,
                reason = "video_sheet_ready"
            )
        }

        if (!observation.page.reliable) {
            return WeChatBehaviorDecision(
                nextState = state,
                capabilityId = WeChatCapabilityId.WAIT,
                reason = "low_page_confidence",
                status = WeChatCapabilityStatus.RUNNING,
                failure = WeChatCapabilityFailure.LOW_PAGE_CONFIDENCE
            )
        }

        if (
            observation.page.page == WeChatSemanticPage.CHAT ||
            observation.page.page == WeChatSemanticPage.CONTACT_DETAIL
        ) {
            if (!observation.targetConversationVerified) {
                return WeChatBehaviorDecision(
                    nextState = state,
                    capabilityId = WeChatCapabilityId.WAIT,
                    reason = "target_not_verified",
                    status = WeChatCapabilityStatus.RUNNING,
                    failure = WeChatCapabilityFailure.TARGET_NOT_VERIFIED
                )
            }
            val selectedRoute = state.selectedRoute ?: WeChatRouteId.CURRENT_CONVERSATION
            return ready(
                state = state.copy(selectedRoute = selectedRoute)
                    .markSucceeded(WeChatCapabilityId.VERIFY_TARGET_CONVERSATION),
                capabilityId = WeChatCapabilityId.OPEN_VIDEO_ENTRY,
                reason = "target_conversation_verified"
            )
        }

        return when (observation.page.page) {
            WeChatSemanticPage.HOME -> decideFromHome(state)
            WeChatSemanticPage.SEARCH -> decideFromSearch(state, observation)
            WeChatSemanticPage.NO_RESULT -> failed(
                state = state.markFailed(
                    WeChatCapabilityId.OPEN_SEARCH_RESULT,
                    WeChatCapabilityFailure.SEARCH_RESULT_NOT_FOUND
                ),
                capabilityId = WeChatCapabilityId.WAIT,
                reason = "search_result_not_found",
                failure = WeChatCapabilityFailure.SEARCH_RESULT_NOT_FOUND
            )
            WeChatSemanticPage.VIDEO_SHEET -> error("handled above")
            WeChatSemanticPage.UNKNOWN -> ready(
                state = state,
                capabilityId = WeChatCapabilityId.RECOVER_HOME,
                reason = "unknown_page"
            )
            WeChatSemanticPage.CHAT,
            WeChatSemanticPage.CONTACT_DETAIL -> error("handled above")
        }
    }

    private fun decideFromHome(state: WeChatBehaviorTreeState): WeChatBehaviorDecision {
        val recentFailed = WeChatCapabilityId.OPEN_RECENT_CONVERSATION in state.failedCapabilities
        return if (!recentFailed && state.selectedRoute != WeChatRouteId.SEARCH) {
            ready(
                state = state.copy(selectedRoute = WeChatRouteId.RECENT_MESSAGES),
                capabilityId = WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                reason = "try_recent_messages"
            )
        } else {
            ready(
                state = state.copy(selectedRoute = WeChatRouteId.SEARCH),
                capabilityId = WeChatCapabilityId.OPEN_SEARCH,
                reason = "fallback_to_search"
            )
        }
    }

    private fun decideFromSearch(
        state: WeChatBehaviorTreeState,
        observation: WeChatCapabilityObservation
    ): WeChatBehaviorDecision {
        val searchState = state.copy(selectedRoute = WeChatRouteId.SEARCH)
            .markSucceeded(WeChatCapabilityId.OPEN_SEARCH)
        return when {
            !observation.searchQueryVerified -> ready(
                state = searchState,
                capabilityId = WeChatCapabilityId.TYPE_CONTACT,
                reason = "search_ready"
            )
            observation.contactAccepted -> ready(
                state = searchState.markSucceeded(WeChatCapabilityId.TYPE_CONTACT),
                capabilityId = WeChatCapabilityId.OPEN_SEARCH_RESULT,
                reason = "search_target_accepted"
            )
            else -> WeChatBehaviorDecision(
                nextState = searchState.markSucceeded(WeChatCapabilityId.TYPE_CONTACT),
                capabilityId = WeChatCapabilityId.WAIT,
                reason = "waiting_search_result",
                status = WeChatCapabilityStatus.RUNNING
            )
        }
    }

    private fun ready(
        state: WeChatBehaviorTreeState,
        capabilityId: WeChatCapabilityId,
        reason: String
    ): WeChatBehaviorDecision = WeChatBehaviorDecision(
        nextState = state,
        capabilityId = capabilityId,
        reason = reason
    )

    private fun failed(
        state: WeChatBehaviorTreeState,
        capabilityId: WeChatCapabilityId,
        reason: String,
        failure: WeChatCapabilityFailure
    ): WeChatBehaviorDecision = WeChatBehaviorDecision(
        nextState = state,
        capabilityId = capabilityId,
        reason = reason,
        status = WeChatCapabilityStatus.FAILED,
        failure = failure
    )

    private fun createCapability(id: WeChatCapabilityId): WeChatCapability = when (id) {
        WeChatCapabilityId.LAUNCH_WECHAT -> SemanticWeChatCapability(
            id,
            isReady = { true },
            isComplete = { it.page.page != WeChatSemanticPage.UNKNOWN }
        )
        WeChatCapabilityId.VERIFY_TARGET_CONVERSATION -> SemanticWeChatCapability(
            id,
            isReady = { it.page.page in conversationPages },
            isComplete = { it.targetConversationVerified }
        )
        WeChatCapabilityId.OPEN_RECENT_CONVERSATION -> SemanticWeChatCapability(
            id,
            isReady = { it.page.page == WeChatSemanticPage.HOME },
            isComplete = { it.targetConversationVerified }
        )
        WeChatCapabilityId.OPEN_SEARCH -> SemanticWeChatCapability(
            id,
            isReady = { it.page.page == WeChatSemanticPage.HOME },
            isComplete = { it.page.page == WeChatSemanticPage.SEARCH }
        )
        WeChatCapabilityId.TYPE_CONTACT -> SemanticWeChatCapability(
            id,
            isReady = { it.page.page == WeChatSemanticPage.SEARCH },
            isComplete = { it.searchQueryVerified }
        )
        WeChatCapabilityId.OPEN_SEARCH_RESULT -> SemanticWeChatCapability(
            id,
            isReady = {
                it.page.page == WeChatSemanticPage.SEARCH && it.contactAccepted
            },
            isComplete = { it.targetConversationVerified }
        )
        WeChatCapabilityId.OPEN_VIDEO_ENTRY -> SemanticWeChatCapability(
            id,
            isReady = {
                it.page.page in conversationPages && it.targetConversationVerified
            },
            isComplete = { it.page.page == WeChatSemanticPage.VIDEO_SHEET }
        )
        WeChatCapabilityId.SELECT_VIDEO -> SemanticWeChatCapability(
            id,
            isReady = { it.page.page == WeChatSemanticPage.VIDEO_SHEET },
            isComplete = { it.callStartedConfirmed }
        )
        WeChatCapabilityId.CONFIRM_CALL_STARTED -> SemanticWeChatCapability(
            id,
            isReady = { it.callStartedConfirmed },
            isComplete = { it.callStartedConfirmed }
        )
        WeChatCapabilityId.RECOVER_HOME,
        WeChatCapabilityId.WAIT -> SemanticWeChatCapability(
            id,
            isReady = { true },
            isComplete = { false }
        )
    }

    private companion object {
        val conversationPages = setOf(
            WeChatSemanticPage.CHAT,
            WeChatSemanticPage.CONTACT_DETAIL
        )
    }
}
