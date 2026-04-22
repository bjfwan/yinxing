package com.yinxing.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAutomationProbe {
    @Test
    fun launcherUiSmoke() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        logStep("开始执行纯 UiAutomator 探针")
        device.logDeviceState("纯 UiAutomator 探针前")
        device.goHomeWithLog()
        device.startLauncherFromInstrumentationAndWaitHome()

        device.clickObjectOrThrow(
            description = "首页电话入口",
            selectors = arrayOf(By.desc("电话"), By.text("电话"))
        )
        device.waitForObjectOrThrow(
            selector = By.res(PACKAGE_NAME, "recycler_contacts"),
            description = "纯 UiAutomator 探针电话联系人列表 recycler_contacts"
        )
        device.pressBackWithLog("纯 UiAutomator 探针返回首页")
        device.waitForObjectOrThrow(
            selector = By.res(PACKAGE_NAME, "recycler_home"),
            description = "纯 UiAutomator 探针首页列表 recycler_home"
        )
        logStep("纯 UiAutomator 探针通过")
    }
}

@RunWith(AndroidJUnit4::class)
class MacrobenchmarkProbe {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupProbe() {
        var enteredMeasureBlock = false
        logStep("开始执行 Macrobenchmark 冷启动探针")
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
            iterations = 1,
            setupBlock = {
                logStep("Macrobenchmark 冷启动探针：setupBlock")
                goHomeWithLog()
            }
        ) {
            enteredMeasureBlock = true
            logStep("Macrobenchmark 冷启动探针：已进入 measure block")
            startLauncherAndWaitHome()
            logStep("Macrobenchmark 冷启动探针：measure block 完成")
        }
        check(enteredMeasureBlock) { "Macrobenchmark 冷启动探针未进入 measure block" }
        logStep("Macrobenchmark 冷启动探针通过")
    }
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileFrameworkProbe {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun collectProbe() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        logStep("开始执行 BaselineProfileRule 框架探针")
        device.logDeviceState("BaselineProfileRule 探针前")

        ProgressWatchdog(
            stepName = "BaselineProfileRule.collect 探针",
            onHeartbeat = {
                device.logDeviceState("等待 BaselineProfileRule.collect 探针时")
            }
        ).start("准备调用 collect")
            .use { watchdog ->
                baselineProfileRule.collect(
                    packageName = PACKAGE_NAME,
                    includeInStartupProfile = false
                ) {
                    watchdog.mark("已进入 lambda")
                    logStep("BaselineProfileRule 探针：lambda 内开始")
                    goHomeWithLog()
                    startLauncherAndWaitHome()
                    logStep("BaselineProfileRule 探针：lambda 内完成")
                    watchdog.mark("lambda 内已完成")
                }
                watchdog.mark("collect 已返回")
            }

        device.logDeviceState("BaselineProfileRule 探针后")
        logStep("BaselineProfileRule 框架探针通过")
    }
}
