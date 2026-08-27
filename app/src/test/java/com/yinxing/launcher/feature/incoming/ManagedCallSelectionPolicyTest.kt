package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedCallSelectionPolicyTest {

    @Test
    fun ringingCallWinsOverExistingActiveCall() {
        val selected = ManagedCallSelectionPolicy.select(
            candidates = listOf(
                ManagedCallCandidate("active", ManagedTelecomCallState.Active),
                ManagedCallCandidate("ringing", ManagedTelecomCallState.Ringing)
            ),
            currentCallId = "active",
            preferredCallId = "ringing"
        )

        assertEquals("ringing", selected?.callId)
    }

    @Test
    fun staleActiveCallbackCannotReplaceCurrentRingingCall() {
        val selected = ManagedCallSelectionPolicy.select(
            candidates = listOf(
                ManagedCallCandidate("old-active", ManagedTelecomCallState.Active),
                ManagedCallCandidate("current-ringing", ManagedTelecomCallState.Ringing)
            ),
            currentCallId = "current-ringing",
            preferredCallId = "old-active"
        )

        assertEquals("current-ringing", selected?.callId)
    }

    @Test
    fun lowerPriorityNewCallCannotReplaceCurrentActiveCall() {
        val selected = ManagedCallSelectionPolicy.select(
            candidates = listOf(
                ManagedCallCandidate("active", ManagedTelecomCallState.Active),
                ManagedCallCandidate("connecting", ManagedTelecomCallState.Connecting)
            ),
            currentCallId = "active",
            preferredCallId = "connecting"
        )

        assertEquals("active", selected?.callId)
    }
}
