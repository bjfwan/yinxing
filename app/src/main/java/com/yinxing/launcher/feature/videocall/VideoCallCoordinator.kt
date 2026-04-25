package com.yinxing.launcher.feature.videocall

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.yinxing.launcher.R
import com.yinxing.launcher.automation.wechat.model.AutomationState
import com.yinxing.launcher.common.service.TTSService
import com.yinxing.launcher.common.util.NetworkUtil
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoCallCoordinator(
    private val activity: AppCompatActivity,
    private val ttsService: TTSService,
    private val contactManager: ContactManager,
    private val automationGateway: VideoCallAutomationGateway,
    private val onNeedAccessibilityPermission: () -> Unit,
    private val onNeedOverlayPermission: (Contact) -> Unit,
    private val onCallCompleted: () -> Unit
) {
    companion object {
        private const val TAG = "VideoCallCoordinator"
    }

    private var activeRequestId: String? = null
    private var activeRequestToken = 0L

    fun start(contact: Contact) {
        if (!NetworkUtil.isNetworkAvailable(activity)) {
            speakAndToast(R.string.network_unavailable_detail, R.string.network_unavailable)
            return
        }

        val serviceName = PermissionUtil.SELECT_TO_SPEAK_SERVICE
        if (!PermissionUtil.isAccessibilityServiceEnabled(activity, serviceName)) {
            speakAndToast(R.string.accessibility_required, R.string.accessibility_required)
            onNeedAccessibilityPermission()
            return
        }

        if (!PermissionUtil.canDrawOverlays(activity)) {
            onNeedOverlayPermission(contact)
            return
        }

        continueWithVideoCall(contact)
    }

    fun continueWithVideoCall(contact: Contact) {
        if (activeRequestId != null) {
            speakAndToast(R.string.video_call_in_progress)
            return
        }

        if (activity.packageManager.getLaunchIntentForPackage("com.tencent.mm") == null) {
            speakAndToast(R.string.wechat_not_installed_detail, R.string.wechat_not_installed)
            return
        }

        val targetName = contact.wechatSearchName?.takeIf { it.isNotBlank() }
        if (contact.requiresWechatSearchName() && targetName == null) {
            speakAndToast(R.string.contact_wechat_search_name_missing)
            return
        }

        speakAndToast(R.string.starting_video_call_detail, R.string.starting_video_call)

        val requestToken = ++activeRequestToken
        var requestId: String? = null
        var pendingTerminalUpdate: VideoCallStateUpdate? = null
        var terminalHandled = false

        fun finishTerminalUpdate(update: VideoCallStateUpdate) {
            if (terminalHandled || requestToken != activeRequestToken) {
                return
            }
            terminalHandled = true
            activeRequestId = null
            requestId?.let(automationGateway::clearRequestListener)
            if (update.success) {
                persistSuccessfulCall(contact.id)
            }
        }

        requestId = automationGateway.requestVideoCall(targetName ?: contact.displayName, VideoCallStateListener { update ->
            activity.lifecycleScope.launch(Dispatchers.Main) {
                if (requestToken != activeRequestToken) {
                    return@launch
                }
                val ttsMessage = buildTtsMessage(update)
                ttsService.speak(ttsMessage)
                Toast.makeText(activity, update.message, Toast.LENGTH_SHORT).show()
                if (!update.terminal) {
                    return@launch
                }
                pendingTerminalUpdate = update
                if (requestId != null) {
                    finishTerminalUpdate(update)
                }
            }
        })
        if (requestToken != activeRequestToken) {
            requestId?.let(automationGateway::clearRequestListener)
            return
        }
        activeRequestId = if (terminalHandled) null else requestId
        pendingTerminalUpdate?.let(::finishTerminalUpdate)
    }

    fun clear() {
        ttsService.stop()
        activeRequestToken++
        activeRequestId?.let(automationGateway::clearRequestListener)
        activeRequestId = null
    }

    private fun persistSuccessfulCall(contactId: String) {
        activity.lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching { contactManager.incrementCallCount(contactId) }
                    .onFailure { Log.e(TAG, "Failed to update video call contact: $contactId", it) }
                    .getOrDefault(false)
            }
            if (!updated) {
                Log.w(TAG, "Video call completed but no contact row was updated: $contactId")
                return@launch
            }
            if (!activity.isFinishing && !activity.isDestroyed) {
                onCallCompleted()
            }
        }
    }

    private fun buildTtsMessage(update: VideoCallStateUpdate): String {
        return when (update.step) {
            AutomationState.LAUNCHING_WECHAT -> "正在打开微信"
            AutomationState.WAITING_HOME -> "正在等待微信首页"
            AutomationState.WAITING_LAUNCHER_UI -> "正在查找联系人"
            AutomationState.WAITING_SEARCH -> "正在搜索联系人"
            AutomationState.WAITING_CONTACT_RESULT -> "找到联系人，正在打开"
            AutomationState.WAITING_CONTACT_DETAIL -> "正在进入联系人详情"
            AutomationState.WAITING_VIDEO_OPTIONS -> "正在发起视频通话"
            AutomationState.RECOVERING -> "页面有变化，正在恢复"
            AutomationState.COMPLETED,
            AutomationState.FAILED,
            AutomationState.IDLE -> update.message
        }
    }

    private fun speakAndToast(detailResId: Int, toastResId: Int) {
        ttsService.speak(activity.getString(detailResId))
        Toast.makeText(activity, activity.getString(toastResId), Toast.LENGTH_SHORT).show()
    }

    private fun speakAndToast(messageResId: Int) {
        val message = activity.getString(messageResId)
        ttsService.speak(message)
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
