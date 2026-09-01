package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.automation.wechat.WeChatClassNames

internal enum class WeChatSemanticPage {
    HOME,
    SEARCH,
    CHAT_INFO,
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
    fun recognize(
        snapshot: WeChatUiSnapshot?,
        currentClass: String? = null,
        expectingVideoSheet: Boolean = false
    ): WeChatSemanticPageResult {
        val semanticResult = if (snapshot == null) {
            WeChatSemanticPageResult(WeChatSemanticPage.UNKNOWN, 0, listOf("missing_snapshot"))
        } else {
            val results = listOf(
                scoreVideoSheet(snapshot),
                scoreNoResult(snapshot),
                scoreChatInfo(snapshot),
                scoreContactDetail(snapshot),
                scoreSearch(snapshot),
                scoreChat(snapshot),
                scoreHome(snapshot)
            )
            results.maxBy { it.confidence }.takeIf { it.confidence > 0 }
                ?: WeChatSemanticPageResult(WeChatSemanticPage.UNKNOWN, 10, listOf("no_known_evidence"))
        }
        if (semanticResult.reliable) {
            return semanticResult
        }
        val classFallback = when {
            expectingVideoSheet && currentClass.orEmpty().startsWith(VIDEO_DIALOG_PREFIX) ->
                WeChatSemanticPage.VIDEO_SHEET to "expected_video_dialog"
            currentClass == WeChatClassNames.LAUNCHER_UI -> WeChatSemanticPage.HOME to "launcher_activity"
            currentClass == WeChatClassNames.CHATTING_UI -> WeChatSemanticPage.CHAT to "chatting_activity"
            currentClass == WeChatClassNames.SINGLE_CHAT_INFO ->
                WeChatSemanticPage.CHAT_INFO to "single_chat_info_activity"
            currentClass == WeChatClassNames.CONTACT_INFO ||
                currentClass == WeChatClassNames.SOS_WEBVIEW ->
                WeChatSemanticPage.CONTACT_DETAIL to "contact_activity"
            currentClass == WeChatClassNames.SEARCH_UI -> WeChatSemanticPage.SEARCH to "search_activity"
            else -> null
        } ?: return semanticResult
        return WeChatSemanticPageResult(
            page = classFallback.first,
            confidence = 75,
            evidence = semanticResult.evidence + classFallback.second
        )
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

    private fun scoreChatInfo(snapshot: WeChatUiSnapshot): WeChatSemanticPageResult {
        if (!WeChatUiSnapshotAnalyzer.isSingleChatInfoPage(snapshot)) {
            return WeChatSemanticPageResult(WeChatSemanticPage.CHAT_INFO, 0, emptyList())
        }
        return WeChatSemanticPageResult(WeChatSemanticPage.CHAT_INFO, 91, listOf("chat_info_actions"))
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

    private const val VIDEO_DIALOG_PREFIX = "com.tencent.mm.ui.widget.dialog."
}
