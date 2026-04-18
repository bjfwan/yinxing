package com.bajianfeng.launcher.feature.incoming

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingCallStatusSmokeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        IncomingCallDiagnostics.clear(context)
        resetLauncherPreferencesSingleton()
    }

    @Test
    fun launchShowsTraceStatusLine() {
        IncomingCallDiagnostics.recordBroadcastReceived(
            context = context,
            callerLabel = "王阿姨",
            incomingNumber = "13812345678",
            autoAnswer = false
        )
        IncomingCallDiagnostics.recordServiceStarted(context, "王阿姨", autoAnswer = false)

        val activity = Robolectric.buildActivity(
            IncomingCallActivity::class.java,
            IncomingCallActivity.buildLaunchIntent(context, "王阿姨", autoAnswer = false)
        ).setup().get()

        val statusView = activity.findViewById<TextView>(R.id.tv_incoming_status)
        assertTrue(statusView.text.contains(activity.getString(R.string.incoming_call_trace_activity)))
        assertTrue(statusView.text.contains("王阿姨"))
    }

    @Test
    fun newIntentWithoutAutoAnswerHidesCountdown() {
        val controller = Robolectric.buildActivity(
            IncomingCallActivity::class.java,
            IncomingCallActivity.buildLaunchIntent(context, "李阿姨", autoAnswer = true)
        ).setup()

        controller.newIntent(
            IncomingCallActivity.buildLaunchIntent(context, "李阿姨", autoAnswer = false)
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val countdownView = controller.get().findViewById<TextView>(R.id.tv_incoming_countdown)
        assertEquals(View.GONE, countdownView.visibility)
        assertTrue(countdownView.text.isEmpty())
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.bajianfeng.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
