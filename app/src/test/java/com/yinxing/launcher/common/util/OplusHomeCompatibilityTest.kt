package com.yinxing.launcher.common.util

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OplusHomeCompatibilityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun compatibilityComponentIsOnlyOfferedToOplusFamily() {
        assertTrue(OplusHomeCompatibility.isSupportedManufacturer("OPPO"))
        assertTrue(OplusHomeCompatibility.isSupportedManufacturer("realme"))
        assertTrue(OplusHomeCompatibility.isSupportedManufacturer("OnePlus"))
        assertFalse(OplusHomeCompatibility.isSupportedManufacturer("vivo"))
    }

    @Test
    fun bundledComponentMatchesTheReviewedArtifact() {
        val encoded = context.resources.openRawResource(R.raw.oplus_home_compat_apk)
            .bufferedReader()
            .use { it.readText() }
        val apk = Base64.decode(encoded, Base64.DEFAULT)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(apk)
            .joinToString("") { "%02X".format(it) }

        assertEquals(OplusHomeCompatibility.EXPECTED_SHA256, hash)
    }
}
