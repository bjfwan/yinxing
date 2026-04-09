package com.bajianfeng.launcher.benchmark

import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutProfile() {
        measureColdStartup(CompilationMode.None())
    }

    @Test
    fun coldStartupWithBaselineProfile() {
        measureColdStartup(CompilationMode.Partial(BaselineProfileMode.UseIfAvailable))
    }

    private fun measureColdStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                StartupTimingMetric(),
                FrameTimingMetric(),
                ArtMetric()
            ),
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
            iterations = 5,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.res(PACKAGE_NAME, "recycler_home")), 5_000)
        }
    }
}
