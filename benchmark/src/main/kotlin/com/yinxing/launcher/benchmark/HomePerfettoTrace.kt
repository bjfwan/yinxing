package com.yinxing.launcher.benchmark

import android.content.Intent
import androidx.benchmark.junit4.PerfettoTraceRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalPerfettoCaptureApi::class)
class HomePerfettoTrace {
    @get:Rule
    val perfettoTraceRule = PerfettoTraceRule()

    @Test
    fun captureHomeTrace() {
        logStep("开始采集首页 Perfetto Trace")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val device = UiDevice.getInstance(instrumentation)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: error("Launch intent not found for $PACKAGE_NAME")

        device.logDeviceState("Perfetto 启动前")
        device.pressHome()
        device.waitForIdle()
        device.logDeviceState("Perfetto 返回桌面后")
        context.startActivity(launchIntent)
        device.waitForIdle()
        device.logDeviceState("Perfetto 启动应用后")
        device.waitForObjectOrThrow(
            selector = By.res(PACKAGE_NAME, "recycler_home"),
            description = "Perfetto 首页列表 recycler_home"
        )
        logStep("Perfetto Trace 首页已稳定，可继续采集")
    }
}

