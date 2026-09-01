package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.common.util.DebugLog

internal data class WeChatDeviceTestScenario(
    val route: WeChatRouteId? = null,
    val failCapabilities: Set<WeChatCapabilityId> = emptySet()
)

internal object WeChatDeviceTestScenarioPolicy {
    fun parse(routeName: String?, failuresCsv: String?): WeChatDeviceTestScenario? {
        val route = routeName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { name -> WeChatRouteId.entries.firstOrNull { it.name == name.uppercase() } }
        if (!routeName.isNullOrBlank() && route == null) return null
        val failures = failuresCsv
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { name ->
                WeChatCapabilityId.entries.firstOrNull { it.name == name.uppercase() }
                    ?: return null
            }
            .toSet()
        if (route == null && failures.isEmpty()) return null
        return WeChatDeviceTestScenario(route = route, failCapabilities = failures)
    }

    fun initialBehaviorState(scenario: WeChatDeviceTestScenario?): WeChatBehaviorTreeState {
        val route = scenario?.route ?: return WeChatBehaviorTreeState()
        var state = WeChatBehaviorTreeState(selectedRoute = route)
        when (route) {
            WeChatRouteId.CONTACTS -> {
                state = state.markFailed(
                    WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                    WeChatCapabilityFailure.RECENT_TARGET_NOT_FOUND
                )
            }
            WeChatRouteId.SEARCH -> {
                state = state
                    .markFailed(
                        WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                        WeChatCapabilityFailure.RECENT_TARGET_NOT_FOUND
                    )
                    .markFailed(
                        WeChatCapabilityId.OPEN_CONTACT_FROM_LIST,
                        WeChatCapabilityFailure.CONTACTS_TARGET_NOT_FOUND
                    )
            }
            WeChatRouteId.CHAT_CONTACT_DETAIL -> {
                state = state.markFailed(
                    WeChatCapabilityId.OPEN_VIDEO_ENTRY,
                    WeChatCapabilityFailure.ACTION_FAILED
                )
            }
            WeChatRouteId.CURRENT_CONVERSATION,
            WeChatRouteId.RECENT_VIDEO_HISTORY,
            WeChatRouteId.RECENT_MESSAGES -> Unit
        }
        return state
    }

    fun useHistoryPreview(
        actualPreview: Boolean,
        scenario: WeChatDeviceTestScenario?
    ): Boolean = when (scenario?.route) {
        WeChatRouteId.RECENT_VIDEO_HISTORY -> true
        WeChatRouteId.RECENT_MESSAGES -> false
        else -> actualPreview
    }
}

internal object WeChatDeviceTestScenarioStore {
    private const val TAG = "WeChatDeviceTest"

    @Volatile
    private var pending: WeChatDeviceTestScenario? = null

    fun arm(routeName: String?, failuresCsv: String?): Boolean {
        if (!BuildConfig.DEBUG) return false
        val scenario = WeChatDeviceTestScenarioPolicy.parse(routeName, failuresCsv) ?: return false
        pending = scenario
        DebugLog.i(TAG) { "armed route=${scenario.route} failures=${scenario.failCapabilities}" }
        return true
    }

    fun clear() {
        if (BuildConfig.DEBUG) pending = null
    }

    fun consume(): WeChatDeviceTestScenario? {
        if (!BuildConfig.DEBUG) return null
        return synchronized(this) {
            pending.also { pending = null }
        }
    }
}
