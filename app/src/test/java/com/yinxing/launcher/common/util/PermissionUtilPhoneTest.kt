package com.yinxing.launcher.common.util

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionUtilPhoneTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).denyPermissions(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
    }

    @Test
    fun phoneReadinessRequiresCallLogPermissionForCallerMatching() {
        shadowOf(application).grantPermissions(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        assertFalse(PermissionUtil.hasPhonePermission(application))

        shadowOf(application).grantPermissions(Manifest.permission.READ_CALL_LOG)
        assertTrue(PermissionUtil.hasPhonePermission(application))
    }
}
