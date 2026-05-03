package com.yinxing.launcher.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFlowBenchmark {
    companion object {
        private const val ITERATIONS = 5
        private const val ACTION_SHOW_INCOMING_CALL = "com.yinxing.launcher.benchmark.SHOW_INCOMING_CALL"
        private const val ACTION_DISMISS_INCOMING_CALL = "com.yinxing.launcher.benchmark.DISMISS_INCOMING_CALL"
        private const val EXTRA_CALLER_NAME = "extra_caller_name"
    }

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openAppManage() {
        measureAndReport("benchmark.open_app_manage") {
            measureFromHome(
                caseName = "打开应用管理",
                entryLabel = "添加",
                targetSelector = By.res(PACKAGE_NAME, "recycler_view"),
                targetDescription = "应用管理列表 recycler_view"
            )
        }
    }

    @Test
    fun loadPhoneContacts() {
        measureAndReport("benchmark.load_phone_contacts") {
            measureFromHome(
                caseName = "加载电话联系人",
                entryLabel = "电话",
                targetSelector = By.res(PACKAGE_NAME, "recycler_phone_contacts"),
                targetDescription = "电话联系人列表 recycler_phone_contacts"
            )
        }
    }

    @Test
    fun returnToHome() {
        measureAndReport("benchmark.return_to_home") {
            benchmarkRule.measureRepeated(
                packageName = PACKAGE_NAME,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.None(),
                iterations = ITERATIONS,
                setupBlock = {
                    logStep("回到桌面：准备测量")
                    startLauncherAndWaitHome()
                    device.clickObjectOrThrow(
                        description = "首页添加入口",
                        selectors = arrayOf(By.desc("添加"), By.text("添加"))
                    )
                    device.waitForObjectOrThrow(
                        selector = By.res(PACKAGE_NAME, "recycler_view"),
                        description = "应用管理列表 recycler_view"
                    )
                }
            ) {
                device.pressBackWithLog("应用管理返回首页")
                device.waitForObjectOrThrow(
                    selector = By.res(PACKAGE_NAME, "recycler_home"),
                    description = "首页列表 recycler_home"
                )
            }
        }
    }

    @Test
    fun showIncomingCallPopup() {
        measureAndReport("benchmark.show_incoming_call") {
            benchmarkRule.measureRepeated(
                packageName = PACKAGE_NAME,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.None(),
                iterations = ITERATIONS,
                setupBlock = {
                    dismissIncomingCall()
                    startLauncherAndWaitHome()
                }
            ) {
                triggerIncomingCall("性能测试来电")
                device.waitForObjectOrThrow(
                    selector = By.res(PACKAGE_NAME, "tv_incoming_caller"),
                    description = "来电页 caller 标题 tv_incoming_caller"
                )
            }
            uiDevice().pressBackWithLog("关闭来电页")
            dismissIncomingCall()
            uiDevice().waitForObjectOrThrow(
                selector = By.res(PACKAGE_NAME, "recycler_home"),
                description = "首页列表 recycler_home"
            )
        }
    }

    private inline fun measureAndReport(metricName: String, block: () -> Unit) {
        val startMs = System.currentTimeMillis()
        block()
        val durationMs = System.currentTimeMillis() - startMs
        BenchmarkReporter.report(metricName, listOf(metricName to durationMs))
    }

    private fun measureFromHome(
        caseName: String,
        entryLabel: String,
        targetSelector: androidx.test.uiautomator.BySelector,
        targetDescription: String
    ) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = ITERATIONS,
            setupBlock = {
                logStep("${caseName}：回到首页准备测量")
                startLauncherAndWaitHome()
            }
        ) {
            device.clickObjectOrThrow(
                description = "首页 ${entryLabel} 入口",
                selectors = arrayOf(By.desc(entryLabel), By.text(entryLabel))
            )
            device.waitForObjectOrThrow(
                selector = targetSelector,
                description = targetDescription
            )
        }
    }

    private fun triggerIncomingCall(callerName: String) {
        benchmarkContext().sendBroadcast(
            Intent(ACTION_SHOW_INCOMING_CALL)
                .setPackage(PACKAGE_NAME)
                .putExtra(EXTRA_CALLER_NAME, callerName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        )
        uiDevice().waitForIdle()
    }

    private fun dismissIncomingCall() {
        benchmarkContext().sendBroadcast(
            Intent(ACTION_DISMISS_INCOMING_CALL)
                .setPackage(PACKAGE_NAME)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        )
        uiDevice().waitForIdle()
    }

    private fun benchmarkContext() = InstrumentationRegistry.getInstrumentation().context

    private fun uiDevice(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}
