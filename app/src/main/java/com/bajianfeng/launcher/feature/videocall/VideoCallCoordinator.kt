package com.bajianfeng.launcher.feature.videocall

import android.content.Context
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.automation.wechat.service.WeChatAccessibilityService
import com.bajianfeng.launcher.common.service.TTSService
import com.bajianfeng.launcher.common.util.NetworkUtil
import com.bajianfeng.launcher.common.util.PermissionUtil
import com.bajianfeng.launcher.data.contact.Contact

class VideoCallCoordinator(
    private val context: Context,
    private val ttsService: TTSService,
    private val onMessage: (String) -> Unit,
    private val onNeedAccessibilityPermission: () -> Unit,
    private val onNeedOverlayPermission: (Contact) -> Unit,
    private val onCallStarted: () -> Unit
) {
    fun start(contact: Contact) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            publish(context.getString(R.string.network_unavailable_detail), context.getString(R.string.network_unavailable))
            return
        }

        val serviceName = "${context.packageName}/${WeChatAccessibilityService::class.java.name}"
        if (!PermissionUtil.isAccessibilityServiceEnabled(context, serviceName)) {
            ttsService.speak(context.getString(R.string.accessibility_required))
            onNeedAccessibilityPermission()
            return
        }

        if (!PermissionUtil.canDrawOverlays(context)) {
            onNeedOverlayPermission(contact)
            return
        }

        continueVideoCall(contact)
    }

    fun clearServiceCallback() {
        WeChatAccessibilityService.getInstance()?.clearStateCallback()
    }

    fun continueWithoutOverlayCheck(contact: Contact) {
        continueVideoCall(contact)
    }

    private fun continueVideoCall(contact: Contact) {
        if (context.packageManager.getLaunchIntentForPackage("com.tencent.mm") == null) {
            publish(
                context.getString(R.string.wechat_not_installed_detail),
                context.getString(R.string.wechat_not_installed)
            )
            return
        }

        publish(
            context.getString(R.string.starting_video_call_detail),
            context.getString(R.string.starting_video_call)
        )

        val service = WeChatAccessibilityService.getInstance()
        if (service == null) {
            publish(
                context.getString(R.string.accessibility_service_not_running),
                context.getString(R.string.accessibility_service_not_running)
            )
            return
        }

        service.setStateCallback { state, success ->
            onMessage(state)
            ttsService.speak(state)
            if (!success || state.contains("已发起")) {
                service.clearStateCallback()
                if (success) {
                    onCallStarted()
                }
            }
        }

        service.requestVideoCall(contact.name)
    }

    private fun publish(spokenMessage: String, visibleMessage: String) {
        ttsService.speak(spokenMessage)
        onMessage(visibleMessage)
    }
}
