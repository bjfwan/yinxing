package com.google.android.accessibility.selecttospeak

import com.yinxing.launcher.common.lobster.LobsterFailureSample
import com.yinxing.launcher.common.lobster.LobsterFailureUiState
import java.security.MessageDigest

internal object WeChatFailureSampleFactory {
    private val safeResourceId = Regex("^com\\.tencent\\.mm:id/[A-Za-z0-9_]{1,80}$")
    private val safeClassName = Regex("^(?:android|androidx|com\\.tencent\\.mm)\\.[A-Za-z0-9_.$]{1,180}$")

    fun create(
        errorCode: String,
        session: WeChatFailureSnapshot,
        route: String?,
        root: WeChatUiSnapshot?,
        windowClass: String?,
        targetVersionName: String? = null,
        targetVersionCode: Long? = null,
    ): LobsterFailureSample {
        val nodes = root?.flatten()?.toList().orEmpty()
        val structuralState = LobsterFailureUiState(
            windowClass = windowClass?.takeIf(safeClassName::matches),
            semanticPage = session.lastSemanticPage,
            route = route,
            resourceIds = nodes.mapNotNull(WeChatUiSnapshot::viewIdResourceName)
                .filter(safeResourceId::matches)
                .distinct()
                .sorted()
                .take(40),
            nodeClasses = nodes.mapNotNull(WeChatUiSnapshot::className)
                .filter(safeClassName::matches)
                .distinct()
                .sorted()
                .take(30),
            nodeCount = nodes.size.coerceAtMost(500),
            clickableCount = nodes.count(WeChatUiSnapshot::clickable).coerceAtMost(500),
            editableCount = nodes.count(WeChatUiSnapshot::editable).coerceAtMost(100),
            maxDepth = root?.let(::maxDepth) ?: 0,
        )
        val canonical = listOf(
            "v1",
            "wechat_video",
            errorCode,
            session.taskStep ?: session.step,
            session.capabilityId.orEmpty(),
            session.capabilityFailure.orEmpty(),
            session.lastSemanticPage.orEmpty(),
            route.orEmpty(),
            structuralState.windowClass.orEmpty(),
        ).joinToString("|")
        return LobsterFailureSample(
            fingerprint = sha256(canonical),
            domain = "wechat_video",
            failureCode = errorCode,
            failedStep = session.taskStep ?: session.step,
            capability = session.capabilityId,
            capabilityFailure = session.capabilityFailure,
            reason = session.taskReason,
            uiState = structuralState,
            targetVersionName = targetVersionName,
            targetVersionCode = targetVersionCode,
        )
    }

    private fun maxDepth(root: WeChatUiSnapshot): Int {
        fun visit(node: WeChatUiSnapshot, depth: Int): Int = node.children
            .maxOfOrNull { child -> visit(child, depth + 1) }
            ?: depth
        return visit(root, 0).coerceIn(0, 32)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
