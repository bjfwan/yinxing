package com.bajianfeng.launcher.feature.videocall

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.util.NetworkUtil
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager
import com.google.android.accessibility.selecttospeak.SelectToSpeakService

class VideoCallCoordinator(
    private val activity: AppCompatActivity,
    private val ttsService: TTSService,
    private val contactManager: ContactManager,
    private val automationGateway: VideoCallAutomationGateway,
    private val onNeedAccessibilityPermission: () -> Unit,
    private val onNeedOverlayPermission: (Contact) -> Unit,
    private val onCallCompleted: () -> Unit
) {
    private var activeRequestId: String? = null

    fun start(contact: Contact) {
        if (!NetworkUtil.isNetworkAvailable(activity)) {
            speakAndToast(R.string.network_unavailable_detail, R.string.network_unavailable)
            return
        }

        val serviceName = "${activity.packageName}/${SelectToSpeakService::class.java.name}"
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

        var terminalDeliveredSynchronously = false
        val requestId = automationGateway.requestVideoCall(targetName ?: contact.displayName, VideoCallStateListener { update ->
            activity.runOnUiThread {
                ttsService.speak(update.message)
                Toast.makeText(activity, update.message, Toast.LENGTH_SHORT).show()
                if (!update.terminal) {
                    return@runOnUiThread
                }
                if (activeRequestId == null) {
                    terminalDeliveredSynchronously = true
                } else {
                    activeRequestId = null
                }
                if (update.success) {
                    contactManager.incrementCallCount(contact.id)
                    onCallCompleted()
                }
            }
        })
        activeRequestId = if (terminalDeliveredSynchronously) null else requestId
    }

    fun clear() {
        ttsService.stop()
        activeRequestId?.let(automationGateway::clearRequestListener)
        activeRequestId = null
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
