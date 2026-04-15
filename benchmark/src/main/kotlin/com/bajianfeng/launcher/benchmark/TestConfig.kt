package com.bajianfeng.launcher.benchmark

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal const val PACKAGE_NAME = "com.bajianfeng.launcher"
internal const val UI_TIMEOUT_MS = 10_000L

private const val BENCHMARK_TAG = "OldLauncherBenchmark"
private const val DIAGNOSTIC_DIR_NAME = "benchmark-diagnostics"
private const val POLL_INTERVAL_MS = 250L
private const val WATCHDOG_INTERVAL_MS = 10_000L

internal fun logStep(message: String) {
    Log.i(BENCHMARK_TAG, message)
    println("[$BENCHMARK_TAG] $message")
    System.err.println("[$BENCHMARK_TAG] $message")
}

internal fun MacrobenchmarkScope.goHomeWithLog() {
    logStep("返回系统桌面")
    pressHome()
    device.waitForIdle()
    device.logDeviceState("已回到桌面")
}

internal fun UiDevice.goHomeWithLog() {
    logStep("返回系统桌面")
    pressHome()
    waitForIdle()
    logDeviceState("已回到桌面")
}

internal fun MacrobenchmarkScope.startLauncherAndWaitHome(timeoutMs: Long = UI_TIMEOUT_MS) {
    logStep("启动 $PACKAGE_NAME")
    startActivityAndWait()
    device.waitForIdle()
    device.logDeviceState("应用已启动")
    device.waitForObjectOrThrow(
        selector = By.res(PACKAGE_NAME, "recycler_home"),
        description = "首页列表 recycler_home",
        timeoutMs = timeoutMs
    )
}

internal fun UiDevice.startLauncherFromInstrumentationAndWaitHome(timeoutMs: Long = UI_TIMEOUT_MS) {
    logStep("通过 instrumentation 启动 $PACKAGE_NAME")
    val context = InstrumentationRegistry.getInstrumentation().context
    context.startActivity(launchTargetAppIntent())
    waitForIdle()
    logDeviceState("通过 instrumentation 启动后")
    waitForObjectOrThrow(
        selector = By.res(PACKAGE_NAME, "recycler_home"),
        description = "首页列表 recycler_home",
        timeoutMs = timeoutMs
    )
}

internal class ProgressWatchdog(
    private val stepName: String,
    private val intervalMs: Long = WATCHDOG_INTERVAL_MS,
    private val onHeartbeat: (() -> Unit)? = null
) : Closeable {
    private val running = AtomicBoolean(false)
    private val stage = AtomicReference("尚未开始")
    private var startedAtMs = 0L
    private var worker: Thread? = null

    fun start(initialStage: String): ProgressWatchdog {
        stage.set(initialStage)
        startedAtMs = SystemClock.elapsedRealtime()
        running.set(true)
        logStep("$stepName：$initialStage")
        worker = Thread {
            while (running.get()) {
                SystemClock.sleep(intervalMs)
                if (!running.get()) {
                    return@Thread
                }
                val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
                logStep("$stepName 仍在进行，已耗时 ${elapsedMs}ms；最近阶段：${stage.get()}")
                runCatching { onHeartbeat?.invoke() }
                    .onFailure { error ->
                        logStep(
                            "$stepName 心跳诊断失败：${error.message ?: error::class.java.simpleName}"
                        )
                    }
            }
        }.apply {
            name = "BenchmarkWatchdog-$stepName"
            isDaemon = true
            start()
        }
        return this
    }

    fun mark(stageMessage: String) {
        stage.set(stageMessage)
        logStep("$stepName：$stageMessage")
    }

    override fun close() {
        running.set(false)
        worker?.interrupt()
        val elapsedMs = if (startedAtMs == 0L) 0L else SystemClock.elapsedRealtime() - startedAtMs
        logStep("$stepName 结束，累计耗时 ${elapsedMs}ms；最终阶段：${stage.get()}")
    }
}


internal fun UiDevice.waitForObjectOrThrow(
    selector: BySelector,
    description: String,
    timeoutMs: Long = UI_TIMEOUT_MS
): UiObject2 {
    logStep("等待 $description（超时 ${timeoutMs}ms）")
    val matched = wait(Until.findObject(selector), timeoutMs)
    if (matched != null) {
        logStep("已出现 $description")
        return matched
    }
    throw AssertionError(buildUiTimeoutMessage(description, timeoutMs))
}

internal fun UiDevice.waitForAnyObjectOrThrow(
    description: String,
    timeoutMs: Long = UI_TIMEOUT_MS,
    vararg selectors: BySelector
): UiObject2 {
    logStep("等待 $description（候选选择器 ${selectors.size} 个，超时 ${timeoutMs}ms）")
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        selectors.firstNotNullOfOrNull { selector -> findObject(selector) }?.let { matched ->
            logStep("已匹配 $description")
            return matched
        }
        waitForIdle()
        SystemClock.sleep(POLL_INTERVAL_MS)
    }
    throw AssertionError(buildUiTimeoutMessage(description, timeoutMs))
}

internal fun UiDevice.clickObjectOrThrow(
    description: String,
    timeoutMs: Long = UI_TIMEOUT_MS,
    vararg selectors: BySelector
) {
    val matched = waitForAnyObjectOrThrow(
        description = description,
        timeoutMs = timeoutMs,
        selectors = selectors
    )
    logStep("点击 $description")
    matched.click()
    waitForIdle()
    logDeviceState("点击 $description 后")
}

internal fun UiDevice.pressBackWithLog(reason: String) {
    logStep("返回上一页：$reason")
    pressBack()
    waitForIdle()
    logDeviceState("返回后：$reason")
}

internal fun UiDevice.logDeviceState(label: String) {
    val focusSummary = runCatching {
        summarizeShellOutput(
            executeShellCommand("dumpsys window windows"),
            listOf("mCurrentFocus", "mFocusedApp")
        )
    }.getOrDefault("窗口焦点读取失败")
    val activitySummary = runCatching {
        summarizeShellOutput(
            executeShellCommand("dumpsys activity activities"),
            listOf("mResumedActivity", "topResumedActivity")
        )
    }.getOrDefault("前台 Activity 读取失败")

    logStep(
        buildString {
            append("设备状态[")
            append(label)
            append("]: currentPackage=")
            append(currentPackageName ?: "unknown")
            append("; focus=")
            append(focusSummary)
            append("; activity=")
            append(activitySummary)
        }
    )
}

private fun launchTargetAppIntent(): Intent {
    val context = InstrumentationRegistry.getInstrumentation().context
    return context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ?: error("Launch intent not found for $PACKAGE_NAME")
}

private fun UiDevice.buildUiTimeoutMessage(description: String, timeoutMs: Long): String {

    val diagnosticsDir = diagnosticsDirectory()
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val screenshotFile = File(diagnosticsDir, "${timestamp}-screen.png")
    val hierarchyFile = File(diagnosticsDir, "${timestamp}-window.xml")
    val screenshotSaved = runCatching { takeScreenshot(screenshotFile) }.getOrDefault(false)
    val hierarchySaved = runCatching { dumpWindowHierarchy(hierarchyFile) }.isSuccess
    val focusSummary = runCatching {
        summarizeShellOutput(
            executeShellCommand("dumpsys window windows"),
            listOf("mCurrentFocus", "mFocusedApp")
        )
    }.getOrDefault("窗口焦点读取失败")
    val activitySummary = runCatching {
        summarizeShellOutput(
            executeShellCommand("dumpsys activity activities"),
            listOf("mResumedActivity", "topResumedActivity")
        )
    }.getOrDefault("前台 Activity 读取失败")

    return buildString {
        append("等待 ")
        append(description)
        append(" 超时 ")
        append(timeoutMs)
        append("ms。currentPackage=")
        append(currentPackageName ?: "unknown")
        append("；focus=")
        append(focusSummary)
        append("；activity=")
        append(activitySummary)
        append("；screenshot=")
        append(if (screenshotSaved) screenshotFile.absolutePath else "未保存")
        append("；hierarchy=")
        append(if (hierarchySaved) hierarchyFile.absolutePath else "未保存")
    }
}

private fun diagnosticsDirectory(): File {
    val arguments = InstrumentationRegistry.getArguments()
    val additionalOutputDir = arguments.getString("additionalTestOutputDir")
    val rootDir = if (!additionalOutputDir.isNullOrBlank()) {
        File(additionalOutputDir)
    } else {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.getExternalFilesDir(null) ?: context.filesDir
    }
    return File(rootDir, DIAGNOSTIC_DIR_NAME).apply { mkdirs() }
}

private fun summarizeShellOutput(output: String, keywords: List<String>): String {
    return output.lineSequence()
        .map { it.trim() }
        .filter { line -> keywords.any(line::contains) }
        .take(6)
        .joinToString(" | ")
        .ifBlank { "未匹配到关键行" }
}

