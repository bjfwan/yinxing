package com.yinxing.launcher.feature.callreturn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallReturnSessionControllerTest {
    private val controller = CallReturnSessionController(ttlMs = 10_000L)

    @Test
    fun disabledSettingDoesNotArmSession() {
        assertFalse(
            controller.arm(
                enabled = false,
                origin = CallReturnOrigin.WECHAT_VIDEO,
                requestId = "wechat-1",
                nowMs = 1_000L
            )
        )

        assertNull(controller.snapshot())
    }

    @Test
    fun unconfirmedCallEndingDoesNotReturnHome() {
        controller.arm(true, CallReturnOrigin.SYSTEM_PHONE, "phone-1", 1_000L)

        assertEquals(
            CallReturnAction.NONE,
            controller.complete(CallReturnOrigin.SYSTEM_PHONE, "phone-1", 2_000L)
        )
        assertNull(controller.snapshot())
    }

    @Test
    fun confirmedCallEndingReturnsHomeOnlyOnce() {
        controller.arm(true, CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 1_000L)
        assertTrue(controller.confirm(CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 2_000L))

        assertEquals(
            CallReturnAction.RETURN_HOME,
            controller.complete(CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 3_000L)
        )
        assertEquals(
            CallReturnAction.NONE,
            controller.complete(CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 4_000L)
        )
    }

    @Test
    fun userLeavingForAnotherAppSuppressesReturn() {
        controller.arm(true, CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 1_000L)
        controller.confirm(CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 2_000L)
        controller.markUserEscaped(2_500L)

        assertEquals(
            CallReturnAction.NONE,
            controller.complete(CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 3_000L)
        )
    }

    @Test
    fun staleSessionCannotReturnHome() {
        controller.arm(true, CallReturnOrigin.SYSTEM_PHONE, "phone-1", 1_000L)
        controller.confirm(CallReturnOrigin.SYSTEM_PHONE, "phone-1", 2_000L)

        assertEquals(
            CallReturnAction.NONE,
            controller.complete(CallReturnOrigin.SYSTEM_PHONE, "phone-1", 12_001L)
        )
    }

    @Test
    fun unrelatedCompletionDoesNotClearActiveSession() {
        controller.arm(true, CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 1_000L)
        controller.confirm(CallReturnOrigin.WECHAT_VIDEO, "wechat-1", 2_000L)

        assertEquals(
            CallReturnAction.NONE,
            controller.complete(CallReturnOrigin.SYSTEM_PHONE, "phone-1", 3_000L)
        )
        assertEquals("wechat-1", controller.snapshot()?.requestId)
    }
}
