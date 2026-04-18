package com.bajianfeng.launcher.feature.incoming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bajianfeng.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingCallDiagnosticsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        IncomingCallDiagnostics.clear(context)
    }

    @Test
    fun emptyTraceShowsPlaceholder() {
        assertEquals(
            context.getString(R.string.settings_incoming_trace_empty),
            IncomingCallDiagnostics.getDisplayText(context)
        )
    }

    @Test
    fun successfulChainBuildsReadableSummary() {
        IncomingCallDiagnostics.recordBroadcastReceived(
            context = context,
            callerLabel = "张阿姨",
            incomingNumber = "13812345678",
            autoAnswer = true
        )
        IncomingCallDiagnostics.recordServiceStarted(context, "张阿姨", autoAnswer = true)
        IncomingCallDiagnostics.recordActivityShown(context, "张阿姨")
        IncomingCallDiagnostics.recordAcceptSuccess(
            context,
            context.getString(R.string.incoming_call_status_accept_sent)
        )
        IncomingCallDiagnostics.recordSpeakerEnabled(context)

        assertEquals(
            listOf(
                context.getString(R.string.incoming_call_trace_broadcast),
                context.getString(R.string.incoming_call_trace_service),
                context.getString(R.string.incoming_call_trace_activity),
                context.getString(R.string.incoming_call_trace_accept_success),
                context.getString(R.string.incoming_call_trace_speaker_on)
            ).joinToString(" · "),
            IncomingCallDiagnostics.getSummaryText(context)
        )
        assertTrue(IncomingCallDiagnostics.getDisplayText(context).contains("张阿姨"))
        assertTrue(
            IncomingCallDiagnostics.getDisplayText(context)
                .contains(context.getString(R.string.incoming_call_status_speaker_on))
        )
    }
}
