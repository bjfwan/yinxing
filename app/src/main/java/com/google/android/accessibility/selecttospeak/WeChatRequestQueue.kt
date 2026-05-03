package com.google.android.accessibility.selecttospeak

import com.google.android.accessibility.selecttospeak.SelectToSpeakService.VideoCallProgress
import com.yinxing.launcher.automation.wechat.model.AutomationState
import java.util.concurrent.atomic.AtomicLong

/**
 * 微信视频请求的全局队列。
 *
 * 之所以从 [SelectToSpeakService] 的 `companion object` 抽出：
 * - 该 companion 同时承担 *服务实例持有*、*请求监听器表*、*待执行请求*、*线程安全广播* 四种职责，
 *   阅读时把"启动协议"和"服务生命周期"绑死了。
 * - 把队列独立成单例对象后，service 只需要在 [SelectToSpeakService.onServiceConnected] 拉取
 *   下一个 [PendingRequest]，并在每次进度变化时调用 [deliverProgress]。
 *
 * 该队列使用 `synchronized(this)` 而非 `Mutex`，因为请求入队/出队是少量短操作，
 * 与 Android 4-API 的回调线程兼容性好；进度回调本身也可能跑在 Binder 线程上。
 */
internal object WeChatRequestQueue {

    private val requestCounter = AtomicLong(0)
    private val requestListeners = linkedMapOf<String, (VideoCallProgress) -> Unit>()
    private var pendingRequest: PendingRequest? = null

    /**
     * 入队一个新的视频请求。返回分配给该请求的 [String] 标识。
     *
     * 行为：
     * - 若当前已有待处理请求，或服务正处于会话中：发送一次 `terminal=true, success=false` 的 busy 进度，结束。
     * - 否则记录为 [pendingRequest] 并：
     *   - 若 service 还未连接，发送一次 "等待无障碍服务连接" 进度；
     *   - 若已连接，立即触发 [WeChatRequestHost.consumePendingRequest]。
     */
    fun enqueue(
        contactName: String,
        listener: (VideoCallProgress) -> Unit,
        host: WeChatRequestHost?
    ): String {
        val requestId = "wechat-call-${System.currentTimeMillis()}-${requestCounter.incrementAndGet()}"
        var shouldNotifyBusy = false
        var shouldNotifyWaiting = false
        var activeHost: WeChatRequestHost? = null
        synchronized(this) {
            requestListeners[requestId] = listener
            if (pendingRequest != null || host?.hasActiveSession() == true) {
                shouldNotifyBusy = true
            } else {
                pendingRequest = PendingRequest(requestId, contactName)
                activeHost = host
                shouldNotifyWaiting = host == null
            }
        }
        if (shouldNotifyBusy) {
            deliverProgress(
                requestId,
                VideoCallProgress(
                    message = "已有进行中的微信视频任务，请稍候",
                    success = false,
                    terminal = true
                )
            )
            return requestId
        }
        if (shouldNotifyWaiting) {
            deliverProgress(
                requestId,
                VideoCallProgress(
                    message = "正在等待无障碍服务连接",
                    success = true,
                    terminal = false
                )
            )
        }
        activeHost?.consumePendingRequest()
        return requestId
    }

    fun clearListener(requestId: String) {
        synchronized(this) {
            requestListeners.remove(requestId)
            if (pendingRequest?.requestId == requestId) {
                pendingRequest = null
            }
        }
    }

    fun resetForTesting() {
        synchronized(this) {
            requestListeners.clear()
            pendingRequest = null
            requestCounter.set(0)
        }
    }

    fun takeNext(): PendingRequest? {
        return synchronized(this) {
            pendingRequest?.also { pendingRequest = null }
        }
    }

    fun deliverProgress(requestId: String, progress: VideoCallProgress) {
        val listener = synchronized(this) { requestListeners[requestId] }
        listener?.invoke(progress)
        if (progress.terminal) {
            clearListener(requestId)
        }
    }
}

/**
 * [WeChatRequestQueue] 与持有自动化会话的 service 之间的最小接口。
 * 抽离后单元测试不再依赖 [SelectToSpeakService] 的全部生命周期。
 */
internal interface WeChatRequestHost {
    fun hasActiveSession(): Boolean
    fun consumePendingRequest()
}

internal data class PendingRequest(
    val requestId: String,
    val contactName: String
)
