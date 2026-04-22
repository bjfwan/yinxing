package com.yinxing.launcher.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestHandlerTest {
    @Test
    fun runOrRequestExecutesActionImmediatelyWhenPermissionIsGranted() {
        var requestedPermission: String? = null
        var executed = false
        val handler = PermissionRequestHandler(
            hasPermission = { permission -> permission == "granted" },
            requestPermission = { permission -> requestedPermission = permission }
        )

        val granted = handler.runOrRequest("granted") {
            executed = true
        }

        assertTrue(granted)
        assertTrue(executed)
        assertNull(requestedPermission)
    }

    @Test
    fun handleResultExecutesPendingActionAfterGrant() {
        var requestedPermission: String? = null
        var executed = false
        val handler = PermissionRequestHandler(
            hasPermission = { false },
            requestPermission = { permission -> requestedPermission = permission }
        )

        val grantedImmediately = handler.runOrRequest("android.permission.READ_CONTACTS") {
            executed = true
        }
        val handled = handler.handleResult("android.permission.READ_CONTACTS", true)

        assertFalse(grantedImmediately)
        assertEquals("android.permission.READ_CONTACTS", requestedPermission)
        assertTrue(handled)
        assertTrue(executed)
    }

    @Test
    fun deniedResultClearsPendingAction() {
        var requestCount = 0
        var executed = false
        val handler = PermissionRequestHandler(
            hasPermission = { false },
            requestPermission = { requestCount++ }
        )

        handler.runOrRequest("android.permission.WRITE_CONTACTS") {
            executed = true
        }

        assertFalse(handler.handleResult("android.permission.WRITE_CONTACTS", false))
        assertFalse(executed)

        handler.runOrRequest("android.permission.WRITE_CONTACTS") {
            executed = true
        }

        assertEquals(2, requestCount)
    }
}
