package com.yinxing.launcher.feature.phone

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemDialerActivityTest {

    @Test
    fun actionDialPrefillsTelephoneNumberWithoutPlacingCall() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:10086"), context, SystemDialerActivity::class.java)

        val activity = Robolectric.buildActivity(SystemDialerActivity::class.java, intent).setup().get()

        assertEquals("10086", activity.findViewById<EditText>(R.id.et_system_dialer_number).text.toString())
        assertTrue(!activity.isFinishing)
    }
}
