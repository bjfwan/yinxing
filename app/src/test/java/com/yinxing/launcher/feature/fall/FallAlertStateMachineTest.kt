package com.yinxing.launcher.feature.fall

import org.junit.Assert.assertEquals
import org.junit.Test

class FallAlertStateMachineTest {

    @Test
    fun countdownStartsAtThirtySecondsAndCallsWhenItExpires() {
        val machine = FallAlertStateMachine()
        assertEquals(FallAlertState.CountingDown(30), machine.state)

        repeat(29) { machine.tick() }
        assertEquals(FallAlertState.CountingDown(1), machine.state)

        machine.tick()
        assertEquals(FallAlertState.CallingFamily, machine.state)
    }

    @Test
    fun userCanCancelBeforeTheCall() {
        val machine = FallAlertStateMachine()
        repeat(10) { machine.tick() }

        machine.cancel()
        machine.tick()

        assertEquals(FallAlertState.Cancelled, machine.state)
    }

    @Test
    fun callNowSkipsTheRemainingCountdown() {
        val machine = FallAlertStateMachine()

        machine.callNow()

        assertEquals(FallAlertState.CallingFamily, machine.state)
    }
}
