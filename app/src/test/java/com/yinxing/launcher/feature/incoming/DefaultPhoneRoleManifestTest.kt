package com.yinxing.launcher.feature.incoming

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultPhoneRoleManifestTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun appExposesDialActivityRequiredByDefaultPhoneRole() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:10086")).setPackage(application.packageName)

        val resolved = application.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        assertNotNull(resolved)
        assertEquals(
            "com.yinxing.launcher.feature.phone.SystemDialerActivity",
            resolved?.activityInfo?.name
        )
    }

    @Test
    fun appExposesExportedInCallServiceRequiredByDefaultPhoneRole() {
        val component = ComponentName(
            application.packageName,
            "com.yinxing.launcher.feature.incoming.YinxingInCallService"
        )

        val info = application.packageManager.getServiceInfo(
            component,
            PackageManager.GET_META_DATA
        )

        assertTrueWithMessage(info.exported, "InCallService 必须允许 Telecom 绑定")
        assertEquals("android.permission.BIND_INCALL_SERVICE", info.permission)
        assertEquals(true, info.metaData?.getBoolean("android.telecom.IN_CALL_SERVICE_UI"))
    }

    private fun assertTrueWithMessage(value: Boolean, message: String) {
        org.junit.Assert.assertTrue(message, value)
    }
}
