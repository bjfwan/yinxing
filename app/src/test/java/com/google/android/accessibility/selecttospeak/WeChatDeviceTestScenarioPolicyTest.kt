package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatDeviceTestScenarioPolicyTest {
    @Test
    fun `contacts command skips recent route only`() {
        val state = WeChatDeviceTestScenarioPolicy.initialBehaviorState(
            WeChatDeviceTestScenario(route = WeChatRouteId.CONTACTS)
        )

        assertEquals(WeChatRouteId.CONTACTS, state.selectedRoute)
        assertTrue(WeChatCapabilityId.OPEN_RECENT_CONVERSATION in state.failedCapabilities)
        assertFalse(WeChatCapabilityId.OPEN_CONTACT_FROM_LIST in state.failedCapabilities)
    }

    @Test
    fun `search command skips recent and contacts routes`() {
        val state = WeChatDeviceTestScenarioPolicy.initialBehaviorState(
            WeChatDeviceTestScenario(route = WeChatRouteId.SEARCH)
        )

        assertEquals(WeChatRouteId.SEARCH, state.selectedRoute)
        assertTrue(WeChatCapabilityId.OPEN_RECENT_CONVERSATION in state.failedCapabilities)
        assertTrue(WeChatCapabilityId.OPEN_CONTACT_FROM_LIST in state.failedCapabilities)
    }

    @Test
    fun `chat detail command makes behavior tree use chat info fallback`() {
        val state = WeChatDeviceTestScenarioPolicy.initialBehaviorState(
            WeChatDeviceTestScenario(route = WeChatRouteId.CHAT_CONTACT_DETAIL)
        )

        assertEquals(WeChatRouteId.CHAT_CONTACT_DETAIL, state.selectedRoute)
        assertTrue(WeChatCapabilityId.OPEN_VIDEO_ENTRY in state.failedCapabilities)
    }

    @Test
    fun `route commands preserve the intended history availability`() {
        assertFalse(
            WeChatDeviceTestScenarioPolicy.useHistoryPreview(
                actualPreview = true,
                scenario = WeChatDeviceTestScenario(route = WeChatRouteId.RECENT_MESSAGES)
            )
        )
        assertTrue(
            WeChatDeviceTestScenarioPolicy.useHistoryPreview(
                actualPreview = false,
                scenario = WeChatDeviceTestScenario(route = WeChatRouteId.RECENT_VIDEO_HISTORY)
            )
        )
        assertTrue(
            WeChatDeviceTestScenarioPolicy.useHistoryPreview(
                actualPreview = true,
                scenario = WeChatDeviceTestScenario(route = WeChatRouteId.CHAT_CONTACT_DETAIL)
            )
        )
    }

    @Test
    fun `command parser rejects unknown route and parses failure list`() {
        assertEquals(
            null,
            WeChatDeviceTestScenarioPolicy.parse(routeName = "not-a-route", failuresCsv = null)
        )
        val scenario = WeChatDeviceTestScenarioPolicy.parse(
            routeName = "RECENT_MESSAGES",
            failuresCsv = "OPEN_RECENT_CONVERSATION,OPEN_VIDEO_ENTRY"
        )

        assertEquals(WeChatRouteId.RECENT_MESSAGES, scenario?.route)
        assertEquals(
            setOf(
                WeChatCapabilityId.OPEN_RECENT_CONVERSATION,
                WeChatCapabilityId.OPEN_VIDEO_ENTRY
            ),
            scenario?.failCapabilities
        )
    }
}
