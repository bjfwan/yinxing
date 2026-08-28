package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTelecomCallControllerTest {

    @Test
    fun telecomLifecycleReportsOnlyRealStateTransitions() {
        val activeStates = mutableListOf<Boolean>()
        val call = FakeManagedTelecomCall("call-transition")
        val controller = ActiveTelecomCallController(
            onCallStateChanged = activeStates::add
        )

        controller.attach(call, ManagedTelecomCallState.Connecting)
        controller.updateState(call.id, ManagedTelecomCallState.Connecting)
        controller.updateState(call.id, ManagedTelecomCallState.Active)
        controller.detach(call.id)

        assertEquals(listOf(true, true, false), activeStates)
    }

    @Test
    fun answerIsDispatchedOnlyOnceWhileCallIsRinging() {
        val call = FakeManagedTelecomCall("call-1")
        val controller = ActiveTelecomCallController()
        controller.attach(call, ManagedTelecomCallState.Ringing)

        assertTrue(controller.answer().dispatched)
        assertFalse(controller.answer().dispatched)
        assertEquals(1, call.answerCount)
    }

    @Test
    fun activeStateConfirmsPreviouslyRequestedAnswer() {
        val call = FakeManagedTelecomCall("call-2")
        val controller = ActiveTelecomCallController()
        controller.attach(call, ManagedTelecomCallState.Ringing)
        controller.answer()

        controller.updateState("call-2", ManagedTelecomCallState.Active)

        val snapshot = controller.snapshot()
        assertEquals(ManagedTelecomCallState.Active, snapshot.state)
        assertTrue(snapshot.answerConfirmed)
    }

    @Test
    fun staleCallbackCannotChangeReplacementCall() {
        val first = FakeManagedTelecomCall("call-1")
        val second = FakeManagedTelecomCall("call-2")
        val controller = ActiveTelecomCallController()
        controller.attach(first, ManagedTelecomCallState.Ringing)
        controller.attach(second, ManagedTelecomCallState.Ringing)

        val matched = controller.updateState("call-1", ManagedTelecomCallState.Disconnected)

        assertFalse(matched)
        assertEquals("call-2", controller.snapshot().callId)
        assertEquals(ManagedTelecomCallState.Ringing, controller.snapshot().state)
    }

    @Test
    fun timedOutAnswerCanBeRetriedWhileCallStillRings() {
        val call = FakeManagedTelecomCall("call-timeout")
        val controller = ActiveTelecomCallController()
        controller.attach(call, ManagedTelecomCallState.Ringing)
        assertTrue(controller.answer().dispatched)

        assertTrue(controller.expireAnswerRequest("call-timeout"))
        assertFalse(controller.snapshot().answerRequested)
        assertTrue(controller.answer().dispatched)
        assertEquals(2, call.answerCount)
    }

    @Test
    fun confirmedAnswerCannotBeExpired() {
        val call = FakeManagedTelecomCall("call-confirmed")
        val controller = ActiveTelecomCallController()
        controller.attach(call, ManagedTelecomCallState.Ringing)
        controller.answer()
        controller.updateState("call-confirmed", ManagedTelecomCallState.Active)

        assertFalse(controller.expireAnswerRequest("call-confirmed"))
        assertTrue(controller.snapshot().answerConfirmed)
    }

    @Test
    fun rejectAndDisconnectUseStateAppropriateCommand() {
        val ringingCall = FakeManagedTelecomCall("call-3")
        val controller = ActiveTelecomCallController()
        controller.attach(ringingCall, ManagedTelecomCallState.Ringing)

        assertTrue(controller.end().dispatched)
        assertEquals(1, ringingCall.rejectCount)
        assertEquals(0, ringingCall.disconnectCount)

        val activeCall = FakeManagedTelecomCall("call-4")
        controller.attach(activeCall, ManagedTelecomCallState.Active)
        assertTrue(controller.end().dispatched)
        assertEquals(1, activeCall.disconnectCount)
    }

    @Test
    fun endRequestStaysIdempotentWhileCallIsDisconnecting() {
        val call = FakeManagedTelecomCall("call-5")
        val controller = ActiveTelecomCallController()
        controller.attach(call, ManagedTelecomCallState.Active)

        assertTrue(controller.end().dispatched)
        controller.updateState("call-5", ManagedTelecomCallState.Connecting)

        assertFalse(controller.end().dispatched)
        assertEquals(1, call.disconnectCount)
    }

    private class FakeManagedTelecomCall(
        override val id: String
    ) : ManagedTelecomCall {
        var answerCount = 0
        var rejectCount = 0
        var disconnectCount = 0

        override fun answerAudioOnly() {
            answerCount += 1
        }

        override fun reject() {
            rejectCount += 1
        }

        override fun disconnect() {
            disconnectCount += 1
        }
    }
}
