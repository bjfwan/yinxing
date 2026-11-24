package com.yinxing.launcher.common.lobster

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue

internal data class LobsterDeliveryResponse(
    val httpStatus: Int? = null,
    val accepted: Boolean = false,
    val retryAfterMs: Long = 0,
    val capabilityUnavailable: Boolean = false
)

internal interface LobsterDeliveryTransport {
    fun send(report: LobsterPendingReport, permitted: () -> Boolean): LobsterDeliveryResponse
    fun cancel()
}

internal object LobsterRetryPolicy {
    const val MAX_BACKOFF_MS = 3_600_000L
    const val MAX_RETRY_AFTER_MS = 86_400_000L
    const val MAX_AGE_MS = 7 * 86_400_000L

    fun retryable(response: LobsterDeliveryResponse): Boolean =
        response.httpStatus == null || response.httpStatus == 408 || response.httpStatus == 429 ||
            response.httpStatus in 500..599 || (response.httpStatus in 200..299 && !response.accepted)

    fun delayMs(id: String, attempts: Int, retryAfterMs: Long = 0): Long {
        val base = (5_000L * (1L shl (attempts - 1).coerceIn(0, 10))).coerceAtMost(MAX_BACKOFF_MS)
        val jitter = (id.hashCode().toLong().absoluteValue % 21) / 100.0
        return maxOf((base * (1 + jitter)).toLong().coerceAtMost(MAX_BACKOFF_MS), retryAfterMs.coerceIn(0, MAX_RETRY_AFTER_MS))
    }
}

/** 一个消费者拥有所有 HTTP 投递；唤醒信号合并，不为每个网络回调创建 flush。 */
internal class LobsterDeliveryEngine(
    private val store: LobsterDeliveryStore,
    private val transport: LobsterDeliveryTransport,
    private val consent: () -> String?,
    private val online: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onStorageFailure: () -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        while (isActive) {
            val waitMs = try {
                drain()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onStorageFailure()
                30_000L
            }
            if (waitMs == null) wake.receive() else withTimeoutOrNull(waitMs.coerceAtLeast(100L)) { wake.receive() }
        }
    }

    suspend fun enqueue(report: LobsterPendingReport): Boolean = withContext(dispatcher) {
        if (consent() != report.consentGeneration) return@withContext false
        val accepted = store.enqueue(report)
        // 撤销与落盘并发时，消费边界仍会按 generation 丢弃，永不复活旧数据。
        trigger()
        accepted
    }

    fun trigger() { wake.trySend(Unit) }

    fun consentChanged() {
        transport.cancel()
        trigger()
    }

    fun close() {
        transport.cancel()
        scope.cancel()
        wake.close()
    }

    internal suspend fun awaitStopped() { worker.join() }

    private fun drain(): Long? {
        var generation = consent()
        if (generation == null) { store.clear(); return null }
        val now = clock()
        store.read().filter { it.consentGeneration != generation || now - it.createdAtMs > LobsterRetryPolicy.MAX_AGE_MS }
            .forEach { store.finish(it, if (it.consentGeneration != generation) "consent_discarded" else "expired", now) }
        if (!online()) return null
        while (scope.isActive) {
            generation = consent() ?: run { store.clear(); return null }
            val currentTime = clock()
            val items = store.read().filter { it.consentGeneration == generation }
            if (items.isEmpty()) return null
            fun due(item: LobsterPendingReport): Long = maxOf(item.nextAttemptAtMs, store.cooldown(item.kind))
                .coerceAtMost(currentTime + LobsterRetryPolicy.MAX_RETRY_AFTER_MS)
            val next = items.filter { due(it) <= currentTime }
                .maxWithOrNull(compareBy<LobsterPendingReport> { it.priority.ordinal }.thenBy { -it.createdAtMs })
                ?: return (items.minOf(::due) - currentTime).coerceAtLeast(100L)
            if (!online()) return null
            val attempted = next.copy(attempts = (next.attempts + 1).coerceAtMost(1000),
                nextAttemptAtMs = currentTime + LobsterRetryPolicy.delayMs(next.id, next.attempts + 1))
            // 在网络开始前写重试状态：进程在响应/删除之间退出仍用同一 delivery_id 恢复。
            store.update(attempted)
            val allowed = { consent() == attempted.consentGeneration && scope.isActive }
            if (!allowed()) continue
            val response = transport.send(attempted, allowed)
            if (!allowed()) continue
            when {
                response.accepted && response.httpStatus in 200..299 -> store.finish(attempted, "acknowledged", clock(), response.httpStatus)
                response.capabilityUnavailable -> {
                    val until = clock() + LobsterRetryPolicy.MAX_BACKOFF_MS
                    store.update(attempted.copy(nextAttemptAtMs = until))
                    store.setCooldown(attempted.kind, until)
                }
                LobsterRetryPolicy.retryable(response) -> {
                    val until = clock() + LobsterRetryPolicy.delayMs(attempted.id, attempted.attempts, response.retryAfterMs)
                    store.update(attempted.copy(nextAttemptAtMs = until))
                    // 同一 endpoint 出故障时，新事件也遵守冷却，不能绕过退避造成请求风暴。
                    store.setCooldown(attempted.kind, until)
                }
                else -> store.finish(attempted, "permanent_rejection", clock(), response.httpStatus)
            }
        }
        return null
    }
}
