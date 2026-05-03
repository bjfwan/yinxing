package com.yinxing.launcher.automation.wechat

object WeChatPackage {
    const val NAME = "com.tencent.mm"
}

object WeChatClassNames {
    const val LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI"
    const val CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI"
    const val CONTACT_INFO = "com.tencent.mm.plugin.profile.ui.ContactInfoUI"
    const val SEARCH_UI = "com.tencent.mm.plugin.fts.ui.FTSMainUI"
    const val SOS_WEBVIEW = "com.tencent.mm.plugin.webview.ui.tools.fts.MMFTSSOSHomeWebViewUI"

    val ALL: Set<String> = setOf(
        LAUNCHER_UI,
        CHATTING_UI,
        CONTACT_INFO,
        SEARCH_UI,
        SOS_WEBVIEW
    )
}

object WeChatViewIds {
    const val MESSAGE_TAB_ICON = "com.tencent.mm:id/icon_tv"
    const val SEARCH_INPUT = "com.tencent.mm:id/d98"
    const val CONTACT_TITLE_PRIMARY = "com.tencent.mm:id/odf"
    const val CONTACT_TITLE_SECONDARY = "com.tencent.mm:id/kbq"

    val CONTACT_RESULT_TITLE_IDS: Set<String> = setOf(
        CONTACT_TITLE_PRIMARY,
        CONTACT_TITLE_SECONDARY
    )

    val MORE_BUTTON_BASE_IDS: List<String> = listOf(
        "com.tencent.mm:id/bjz",
        "com.tencent.mm:id/j7s",
        "com.tencent.mm:id/more_options"
    )

    val MORE_BUTTON_FALLBACK_IDS: List<String> = MORE_BUTTON_BASE_IDS + listOf(
        "com.tencent.mm:id/b9s",
        "com.tencent.mm:id/aqy"
    )

    val TOP_SEARCH_BAR_IDS: List<String> = listOf(
        "com.tencent.mm:id/jha",
        "com.tencent.mm:id/meb",
        "com.tencent.mm:id/f8s",
        "com.tencent.mm:id/d6o",
        "com.tencent.mm:id/e6j",
        "com.tencent.mm:id/hbz",
        "com.tencent.mm:id/ibp"
    )
}
