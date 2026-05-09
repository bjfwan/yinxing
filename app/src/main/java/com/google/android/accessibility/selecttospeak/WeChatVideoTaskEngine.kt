package com.google.android.accessibility.selecttospeak

internal enum class WeChatSemanticPage {
    HOME,
    SEARCH,
    CONTACT_DETAIL,
    CHAT,
    VIDEO_SHEET,
    NO_RESULT,
    UNKNOWN
}

internal data class WeChatSemanticPageResult(
    val page: WeChatSemanticPage,
    val confidence: Int,
    val evidence: List<String>
) {
    val reliable: Boolean
        get() = confidence >= 70
}

internal object WeChatSemanticPageRecognizer {
    fun recognize(snapshot: WeChatUiSnapshot?): WeChatSemanticPageResult {
        if (snapshot == null) {
            return WeChatSemanticPageResult(WeChatSemanticPage.UNKNOWN, 0, listOf("missing_snapshot"))
        }
        val results = listOf(
            scoreVideoSheet(snapshot),
            scoreNoResult(snapshot),
            scoreContactDetail(snapshot),
            scoreSearch(snapshot),
            scoreChat(snapshot),
            scoreHome(snapshot)
        )
        return results.maxBy { it.confidence }.takeIf { it.confidence > 0 }
            ?: WeChatSemanticPageResult(WeChatSemanticPage.UNKNOWN, 10, listOf("no_known_evidence"))
    }

    private fun scoreHome(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.isLauncherReady(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.HOME, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.HOME, 90, listOf("main_tabs"))
    }

    private fun scoreSearch(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.isSearchPage(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.SEARCH, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.SEARCH, 86, listOf("editable", "search_chrome"))
    }

    private fun scoreContactDetail(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.isContactInfoPage(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.CONTACT_DETAIL, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.CONTACT_DETAIL, 88, listOf("contact_actions"))
    }

    private fun scoreChat(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.isChatPageLike(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.CHAT, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.CHAT, 82, listOf("editable", "conversation_chrome"))
    }

    private fun scoreVideoSheet(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.isVideoCallSheetVisible(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.VIDEO_SHEET, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.VIDEO_SHEET, 95, listOf("video_option", "voice_option", "cancel"))
    }

    private fun scoreNoResult(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.hasNoSearchResult(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.NO_RESULT, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.NO_RESULT, 92, listOf("no_search_result"))
    }
}

internal enum class WeChatVideoTaskStep {
    WAITING_HOME,
    WAITING_SEARCH,
    WAITING_CONTACT_RESULT,
    WAITING_CONTACT_DETAIL,
    WAITING_VIDEO_OPTIONS,
    COMPLETED,
    FAILED
}

internal enum class WeChatVideoTaskAction {
    OPEN_SEARCH,
    TYPE_CONTACT,
    OPEN_CONTACT,
    OPEN_VIDEO_ENTRY,
    CONFIRM_VIDEO_CALL,
    WAIT,
    RECOVER_HOME,
    COMPLETE,
    FAIL
}

internal data class WeChatVideoTaskState(
    val step: WeChatVideoTaskStep = WeChatVideoTaskStep.WAITING_HOME,
    val contactName: String,
    val attempts: Map<WeChatVideoTaskStep, Int> = emptyMap(),
    val resolvedDisplayName: String? = null
)

internal data class WeChatVideoTaskDecision(
    val nextState: WeChatVideoTaskState,
    val action: WeChatVideoTaskAction,
    val reason: String
)

internal class WeChatVideoTaskEngine(
    private val maxAttemptsPerStep: Int = 3
) {
    fun decide(
        state: WeChatVideoTaskState,
        page: WeChatSemanticPageResult,
        contactScore: WeChatTargetScore? = null
    ): WeChatVideoTaskDecision {
        if (!page.reliable && state.step != WeChatVideoTaskStep.COMPLETED) {
            return retryOrRecover(state, "low_confidence_${page.page.name.lowercase()}")
        }
        return when (state.step) {
            WeChatVideoTaskStep.WAITING_HOME -> when (page.page) {
                WeChatSemanticPage.HOME -> next(state, WeChatVideoTaskStep.WAITING_SEARCH, WeChatVideoTaskAction.OPEN_SEARCH, "home_ready")
                else -> retryOrRecover(state, "need_home")
            }
            WeChatVideoTaskStep.WAITING_SEARCH -> when (page.page) {
                WeChatSemanticPage.SEARCH -> next(state, WeChatVideoTaskStep.WAITING_CONTACT_RESULT, WeChatVideoTaskAction.TYPE_CONTACT, "search_ready")
                else -> retryOrRecover(state, "need_search")
            }
            WeChatVideoTaskStep.WAITING_CONTACT_RESULT -> when {
                page.page == WeChatSemanticPage.NO_RESULT -> fail(state, "no_contact_result")
                contactScore?.accepted == true -> next(
                    state.copy(resolvedDisplayName = contactScore.displayName),
                    WeChatVideoTaskStep.WAITING_CONTACT_DETAIL,
                    WeChatVideoTaskAction.OPEN_CONTACT,
                    "contact_score_${contactScore.score}"
                )
                else -> retryOrRecover(state, "weak_contact_result")
            }
            WeChatVideoTaskStep.WAITING_CONTACT_DETAIL -> when (page.page) {
                WeChatSemanticPage.CONTACT_DETAIL, WeChatSemanticPage.CHAT -> next(
                    state,
                    WeChatVideoTaskStep.WAITING_VIDEO_OPTIONS,
                    WeChatVideoTaskAction.OPEN_VIDEO_ENTRY,
                    "contact_ready"
                )
                else -> retryOrRecover(state, "need_contact_detail")
            }
            WeChatVideoTaskStep.WAITING_VIDEO_OPTIONS -> when (page.page) {
                WeChatSemanticPage.VIDEO_SHEET -> next(
                    state,
                    WeChatVideoTaskStep.COMPLETED,
                    WeChatVideoTaskAction.CONFIRM_VIDEO_CALL,
                    "video_sheet_ready"
                )
                else -> retryOrRecover(state, "need_video_sheet")
            }
            WeChatVideoTaskStep.COMPLETED -> WeChatVideoTaskDecision(state, WeChatVideoTaskAction.COMPLETE, "completed")
            WeChatVideoTaskStep.FAILED -> WeChatVideoTaskDecision(state, WeChatVideoTaskAction.FAIL, "failed")
        }
    }

    private fun next(
        state: WeChatVideoTaskState,
        step: WeChatVideoTaskStep,
        action: WeChatVideoTaskAction,
        reason: String
    ): WeChatVideoTaskDecision {
        return WeChatVideoTaskDecision(state.copy(step = step), action, reason)
    }

    private fun retryOrRecover(state: WeChatVideoTaskState, reason: String): WeChatVideoTaskDecision {
        val attempts = state.attempts[state.step].orZero() + 1
        val nextState = state.copy(attempts = state.attempts + (state.step to attempts))
        return if (attempts >= maxAttemptsPerStep) {
            WeChatVideoTaskDecision(
                nextState.copy(step = WeChatVideoTaskStep.WAITING_HOME),
                WeChatVideoTaskAction.RECOVER_HOME,
                reason
            )
        } else {
            WeChatVideoTaskDecision(nextState, WeChatVideoTaskAction.WAIT, reason)
        }
    }

    private fun fail(state: WeChatVideoTaskState, reason: String): WeChatVideoTaskDecision {
        return WeChatVideoTaskDecision(state.copy(step = WeChatVideoTaskStep.FAILED), WeChatVideoTaskAction.FAIL, reason)
    }

    private fun Int?.orZero(): Int = this ?: 0
}
