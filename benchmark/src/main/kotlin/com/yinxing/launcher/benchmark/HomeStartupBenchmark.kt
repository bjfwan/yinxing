package com.yinxing.launcher.benchmark

import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutProfile() {
        measureColdStartup(
            caseName = "冷启动（无 Baseline Profile）",
            compilationMode = CompilationMode.None()
        )
    }

    @Test
    fun coldStartupWithBaselineProfile() {
        measureColdStartup(
            caseName = "冷启动（使用 Baseline Profile）",
            compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable)
        )
    }

    private fun measureColdStartup(caseName: String, compilationMode: CompilationMode) {
        val iterations = 5
        var currentIteration = 0
        logStep("开始执行 $caseName")
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                StartupTimingMetric(),
                FrameTimingMetric(),
                ArtMetric()
            ),
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
            iterations = iterations,
            setupBlock = {
                logStep("$caseName 准备第 ${currentIteration + 1}/$iterations 次：返回桌面")
                goHomeWithLog()
            }
        ) {
            currentIteration += 1
            logStep("$caseName 第 $currentIteration/$iterations 次：启动应用")
            startActivityAndWait()
            device.waitForIdle()
            device.logDeviceState("$caseName 第 $currentIteration 次启动后")
            device.waitForObjectOrThrow(
                selector = By.res(PACKAGE_NAME, "recycler_home"),
                description = "$caseName 第 $currentIteration 次首页列表 recycler_home"
            )
            logStep("$caseName 第 $currentIteration/$iterations 次完成")
        }
        logStep("$caseName 执行完成")
    }
}

