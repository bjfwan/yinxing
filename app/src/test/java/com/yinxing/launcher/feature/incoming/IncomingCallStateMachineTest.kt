package com.yinxing.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingCallStateMachineTest {

    private lateinit var machine: IncomingCallStateMachine

    @Before
    fun setUp() {
        machine = IncomingCallStateMachine()
    }

    @Test
    fun initialStateIsIdle() {
        assertTrue(machine.state is IncomingCallState.Idle)
    }

    @Test
    fun customInitialState() {
        val m = IncomingCallStateMachine(IncomingCallState.Answered)
        assertTrue(m.state is IncomingCallState.Answered)
    }

    @Test
    fun ringingTrimsAndNullsBlankStrings() {
        val state = machine.ringing("  张阿姨  ", "  13812345678  ", true)
        state as IncomingCallState.Ringing
        assertEquals("张阿姨", state.callerLabel)
        assertEquals("13812345678", state.incomingNumber)
        assertTrue(state.autoAnswer)
    }

    @Test
    fun ringingConvertsBlankCallerLabelToNull() {
        val state = machine.ringing("   ", "13812345678", false)
        state as IncomingCallState.Ringing
        assertNull(state.callerLabel)
        assertNotNull(state.incomingNumber)
    }

    @Test
    fun ringingConvertsBlankNumberToNull() {
        val state = machine.ringing("张阿姨", "  ", false)
        state as IncomingCallState.Ringing
        assertNotNull(state.callerLabel)
        assertNull(state.incomingNumber)
    }

    @Test
    fun foregroundServiceStartedTransitionsToShowingUi() {
        machine.ringing("张阿姨", "13812345678", true)
        val state = machine.foregroundServiceStarted("张阿姨", true)
        state as IncomingCallState.ShowingUi
        assertEquals("张阿姨", state.callerLabel)
        assertTrue(state.autoAnswer)
    }

    @Test
    fun uiShownWithAutoAnswerProducesWaitingAutoAnswer() {
        val state = machine.uiShown("张阿姨", autoAnswer = true, autoAnswerDelaySeconds = 5)
        state as IncomingCallState.WaitingAutoAnswer
        assertEquals("张阿姨", state.callerLabel)
        assertEquals(5, state.delaySeconds)
    }

    @Test
    fun uiShownWithoutAutoAnswerProducesShowingUi() {
        val state = machine.uiShown("张阿姨", autoAnswer = false, autoAnswerDelaySeconds = 5)
        state as IncomingCallState.ShowingUi
        assertFalse(state.autoAnswer)
    }

    @Test
    fun uiShownClampsDelayToOneMinimum() {
        val state = machine.uiShown("张阿姨", autoAnswer = true, autoAnswerDelaySeconds = -10)
        state as IncomingCallState.WaitingAutoAnswer
        assertEquals(1, state.delaySeconds)
    }

    @Test
    fun uiShownClampsDelayToThirtyMaximum() {
        val state = machine.uiShown("张阿姨", autoAnswer = true, autoAnswerDelaySeconds = 999)
        state as IncomingCallState.WaitingAutoAnswer
        assertEquals(30, state.delaySeconds)
    }

    @Test
    fun answeredTransitionsToAnswered() {
        machine.ringing("张阿姨", "13812345678", false)
        val state = machine.answered()
        assertTrue(state is IncomingCallState.Answered)
    }

    @Test
    fun rejectedTransitionsToRejected() {
        machine.ringing("张阿姨", "13812345678", false)
        val state = machine.rejected()
        assertTrue(state is IncomingCallState.Rejected)
    }

    @Test
    fun failedTransitionsToFailedWithReason() {
        val reason = IncomingCallFailureReason(IncomingCallFailureCategory.PhonePermission, "缺少权限")
        val state = machine.failed(reason)
        state as IncomingCallState.Failed
        assertEquals(IncomingCallFailureCategory.PhonePermission, state.reason.category)
        assertEquals("缺少权限", state.reason.detail)
    }

    @Test
    fun idleResetsToIdle() {
        machine.ringing("张阿姨", "13812345678", true)
        machine.answered()
        val state = machine.idle()
        assertTrue(state is IncomingCallState.Idle)
    }

    @Test
    fun fullSuccessfulCallChain() {
        machine.ringing("张阿姨", "13812345678", true)
        assertTrue(machine.state is IncomingCallState.Ringing)

        machine.foregroundServiceStarted("张阿姨", true)
        assertTrue(machine.state is IncomingCallState.ShowingUi)

        val uiState = machine.uiShown("张阿姨", autoAnswer = true, autoAnswerDelaySeconds = 3)
        uiState as IncomingCallState.WaitingAutoAnswer
        assertEquals(3, uiState.delaySeconds)

        machine.answered()
        assertTrue(machine.state is IncomingCallState.Answered)
    }

    @Test
    fun fullRejectedCallChain() {
        machine.ringing("骚扰电话", null, false)
        machine.foregroundServiceStarted("骚扰电话", false)
        machine.uiShown("骚扰电话", autoAnswer = false, autoAnswerDelaySeconds = 5)
        assertTrue(machine.state is IncomingCallState.ShowingUi)

        machine.rejected()
        assertTrue(machine.state is IncomingCallState.Rejected)
    }

    @Test
    fun failedCallChain() {
        machine.ringing(null, "", false)
        val reason = IncomingCallFailureReason(IncomingCallFailureCategory.Overlay, "无悬浮窗权限")
        machine.failed(reason)
        assertTrue(machine.state is IncomingCallState.Failed)
    }
}
