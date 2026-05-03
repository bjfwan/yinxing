package com.google.android.accessibility.selecttospeak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 微信视频自动化的"时钟"管理器。
 *
 * 之所以从 [SelectToSpeakService] 抽出：
 * - 服务里同时有 [processJob]/[stepTimeoutJob]/[totalTimeoutJob] 三个并行 [Job]，
 *   彼此都要走 `xxJob?.cancel(); xxJob = ...launch {}` 的相同模板；
 * - 取消逻辑分散在 `cancelSession`、`transitionTo`、`rerouteTo`、`armTimeout` 等多处，
 *   一旦哪里漏一行 cancel 就会泄露 Job；
 * - 调用方只关心"现在请安排下一次推进，并设置一个可被取消的步骤超时"，与 Job 字段管理无关。
 *
 * 该 Clock **不持有** session 数据，仅通过回调与服务交互：
 * - [onProcessTick]：到点了，请推进一次状态机（即原 `processCurrentWindow`）。
 * - [onTimeoutFailure]：步骤或总流程超时，请按失败处理。
 * - [sessionStillActive]：判断 "延迟到时之后，会话是否还存在"，用于总超时短路。
 */
internal class VideoCallStepClock(
    private val scope: CoroutineScope,
    private val onProcessTick: () -> Unit,
    private val onTimeoutFailure: (message: String) -> Unit,
    private val sessionStillActive: () -> Boolean
) {
    private var processJob: Job? = null
    private var stepTimeoutJob: Job? = null
    private var totalTimeoutJob: Job? = null

    fun scheduleProcess(delayMillis: Long) {
        processJob?.cancel()
        processJob = scope.launch {
            delay(delayMillis)
            onProcessTick()
        }
    }

    fun cancelProcess() {
        processJob?.cancel()
        processJob = null
    }

    /**
     * @param stepStillCurrent 由调用方在到期时回答 "现在是否还卡在原 step"，由调用方持有 step 引用。
     */
    fun armStepTimeout(
        timeoutMillis: Long,
        failureMessage: String,
        stepStillCurrent: () -> Boolean
    ) {
        stepTimeoutJob?.cancel()
        stepTimeoutJob = scope.launch {
            delay(timeoutMillis)
            if (stepStillCurrent()) {
                onTimeoutFailure(failureMessage)
            }
        }
    }

    fun cancelStepTimeout() {
        stepTimeoutJob?.cancel()
        stepTimeoutJob = null
    }

    fun armTotalTimeout(timeoutMillis: Long, failureMessage: () -> String) {
        totalTimeoutJob?.cancel()
        totalTimeoutJob = scope.launch {
            delay(timeoutMillis)
            if (sessionStillActive()) {
                onTimeoutFailure(failureMessage())
            }
        }
    }

    fun cancelAll() {
        processJob?.cancel()
        stepTimeoutJob?.cancel()
        totalTimeoutJob?.cancel()
        processJob = null
        stepTimeoutJob = null
        totalTimeoutJob = null
    }
}
