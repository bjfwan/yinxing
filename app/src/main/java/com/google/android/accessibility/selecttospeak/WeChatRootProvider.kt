package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.yinxing.launcher.automation.wechat.WeChatClassNames
import com.yinxing.launcher.automation.wechat.WeChatPackage
import com.yinxing.launcher.automation.wechat.util.AccessibilityUtil
import com.yinxing.launcher.common.util.DebugLog

/**
 * 获取并缓存"当前最有可能代表微信窗口"的 [AccessibilityNodeInfo]。
 *
 * 之所以从 [SelectToSpeakService] 抽出：
 * 服务里同时混着 *会话状态机*、*超时调度*、*微信节点查询*、*微信类名记忆* 多个关注点，
 * 而本类的 6-7 个方法 (`updateCachedWeChatRoot` / `getWeChatRoot` / `findWeChatRootInWindows` /
 * `isUsableWeChatRoot` / `extractRoot` / `replaceCachedWeChatRoot` / `resolveCurrentWeChatClass` /
 * `isMeaningfulWeChatClassName`) 仅读 Accessibility API + 比对包名/类名，无任何会话/UI/Job 副作用。
 *
 * 把它独立成 Provider 后：
 * - service 主体减少近 120 行噪音；
 * - 将来加入更多 root 选择策略（多 window 检测）只需要在本类做。
 */
internal class WeChatRootProvider(
    private val service: AccessibilityService
) {
    private companion object {
        const val TAG = "WeChatRootProvider"
    }

    private var cachedRoot: AccessibilityNodeInfo? = null
    private var lastClassName: String? = null

    /** 仅用于日志/调试场景的只读访问；调用方不得 recycle。 */
    val lastObservedClassName: String? get() = lastClassName

    /** 仅用于 `obtainSpeakerTargetRoot` 场景，由调用方决定是否 [AccessibilityNodeInfo.obtain] 拷贝。 */
    fun peekCachedRoot(): AccessibilityNodeInfo? = cachedRoot

    /**
     * 收到事件时调用，更新（必要时）缓存的 root。
     * 不返回值；如不可用，会被丢弃 + recycle。
     */
    fun updateFromEvent(source: AccessibilityNodeInfo?) {
        val root = source?.let { extractRoot(it) } ?: return
        if (root.packageName?.toString() != WeChatPackage.NAME) {
            AccessibilityUtil.safeRecycle(root)
            return
        }
        val copy = AccessibilityNodeInfo.obtain(root)
        if (!isUsableWeChatRoot(copy)) {
            DebugLog.d(TAG) { "updateFromEvent: discard unusable root ${AccessibilityUtil.summarizeNode(copy)}" }
            AccessibilityUtil.safeRecycle(copy)
            return
        }
        replaceCached(copy)
        DebugLog.d(TAG) { "updateFromEvent: cached root class=${copy.className} childCount=${copy.childCount}" }
    }

    /**
     * 选取一个最适合表达当前微信状态的 root；选中后会替换缓存。
     */
    fun acquireBestRoot(): AccessibilityNodeInfo? {
        val best = findWeChatRootInWindows()
        if (best != null) {
            DebugLog.d(TAG) { "acquireBestRoot: window class=${best.className} childCount=${best.childCount}" }
            replaceCached(best)
            return best
        }
        DebugLog.d(TAG) { "acquireBestRoot: no usable WeChat window" }
        return null
    }

    fun resolveCurrentWeChatClass(root: AccessibilityNodeInfo?): String? {
        val rootClass = root?.className?.toString()
        if (rootClass in WeChatClassNames.ALL) {
            return rootClass
        }
        if (isMeaningfulClassName(lastClassName)) {
            return lastClassName
        }
        return rootClass
    }

    fun rememberClassName(className: String?) {
        if (isMeaningfulClassName(className)) {
            lastClassName = className
        }
    }

    fun isMeaningfulClassName(className: String?): Boolean {
        return !className.isNullOrBlank() && className.startsWith(WeChatPackage.NAME)
    }

    fun reset() {
        AccessibilityUtil.safeRecycle(cachedRoot)
        cachedRoot = null
        lastClassName = null
    }

    private fun extractRoot(source: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = source
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    private fun replaceCached(node: AccessibilityNodeInfo?) {
        if (cachedRoot === node) {
            return
        }
        AccessibilityUtil.safeRecycle(cachedRoot)
        cachedRoot = node
    }

    private fun isUsableWeChatRoot(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val pkg = node.packageName?.toString()
        if (pkg != WeChatPackage.NAME) return false
        val className = node.className?.toString().orEmpty()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val hasBounds = !bounds.isEmpty
        val hasChildren = node.childCount > 0
        if (!hasChildren && className.isBlank()) return false
        if (!hasBounds && !hasChildren) return false
        val refreshed = runCatching { node.refresh() }.getOrDefault(false)
        if (className in WeChatClassNames.ALL) return true
        return hasChildren || (refreshed && hasBounds && className.isNotBlank())
    }

    private fun getWindowRoots(): List<AccessibilityNodeInfo> {
        return service.windows
            .orEmpty()
            .sortedWith(
                compareByDescending<AccessibilityWindowInfo> { it.isActive }
                    .thenByDescending { it.isFocused }
                    .thenByDescending { it.layer }
            )
            .mapNotNull { it.root }
    }

    private fun findWeChatRootInWindows(): AccessibilityNodeInfo? {
        val activeRoot = service.rootInActiveWindow
        if (isUsableWeChatRoot(activeRoot)) {
            return activeRoot
        }
        AccessibilityUtil.safeRecycle(activeRoot)
        for (root in getWindowRoots()) {
            if (isUsableWeChatRoot(root)) {
                return root
            }
            AccessibilityUtil.safeRecycle(root)
        }
        return null
    }
}
