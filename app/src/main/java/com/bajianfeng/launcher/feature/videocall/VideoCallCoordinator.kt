package com.bajianfeng.launcher.feature.videocall

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.automation.wechat.service.WeChatAccessibilityService
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.util.NetworkUtil
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager

class VideoCallCoordinator(
    private val activity: AppCompatActivity,
    private val ttsService: TTSService,
    private val contactManager: ContactManager,
    private val onNeedAccessibilityPermission: () -> Unit,
    private val onNeedOverlayPermission: (Contact) -> Unit,
    private val onContactsChanged: () -> Unit,
    private val onCallCompleted: () -> Unit
) {
    fun start(contact: Contact) {
        if (!NetworkUtil.isNetworkAvailable(activity)) {
            speakAndToast(R.string.network_unavailable_detail, R.string.network_unavailable)
            return
        }

        val serviceName = "${activity.packageName}/${WeChatAccessibilityService::class.java.name}"
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
        if (activity.packageManager.getLaunchIntentForPackage("com.tencent.mm") == null) {
            speakAndToast(R.string.wechat_not_installed_detail, R.string.wechat_not_installed)
            return
        }

        val service = WeChatAccessibilityService.getInstance()
        if (service == null) {
            speakAndToast(R.string.accessibility_service_not_running, R.string.accessibility_service_not_running)
            return
        }

        speakAndToast(R.string.starting_video_call_detail, R.string.starting_video_call)
        service.setStateCallback { state, success ->
            activity.runOnUiThread {
                ttsService.speak(state)
                Toast.makeText(activity, state, Toast.LENGTH_SHORT).show()
                if (!success || state.contains("已发起")) {
                    service.clearStateCallback()
                    if (success) {
                        onCallCompleted()
                    }
                }
            }
        }

        contactManager.incrementCallCount(contact.id)
        onContactsChanged()
        service.requestVideoCall(contact.name)
    }

    fun clear() {
        ttsService.stop()
        WeChatAccessibilityService.getInstance()?.clearStateCallback()
    }

    private fun speakAndToast(detailResId: Int, toastResId: Int) {
        ttsService.speak(activity.getString(detailResId))
        Toast.makeText(activity, activity.getString(toastResId), Toast.LENGTH_SHORT).show()
    }
}
