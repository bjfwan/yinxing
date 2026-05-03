package com.yinxing.launcher.feature.incoming

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yinxing.launcher.data.home.LauncherPreferences
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingCallSimulatorTest {

    @Test
    fun simulateIncomingCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = LauncherPreferences.getInstance(context)
        prefs.setAutoAnswerEnabled(true)
        prefs.setAutoAnswerDelaySeconds(10)
        IncomingCallForegroundService.start(
            context = context,
            callerName = "测试来电",
            autoAnswer = true,
            incomingNumber = "13800138000",
            knownContact = false
        )
        Thread.sleep(30000)
    }
}
