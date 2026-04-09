package com.bajianfeng.launcher.benchmark

import android.content.Intent
import androidx.benchmark.junit4.PerfettoTraceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePerfettoTrace {
    @get:Rule
    val perfettoTraceRule = PerfettoTraceRule()

    @Test
    fun captureHomeTrace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val device = UiDevice.getInstance(instrumentation)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: error("Launch intent not found for $PACKAGE_NAME")

        device.pressHome()
        context.startActivity(launchIntent)
        device.wait(Until.hasObject(By.res(PACKAGE_NAME, "recycler_home")), 5_000)
    }
}
