package com.yinxing.launcher.feature.incoming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.KeyEvent
import com.yinxing.launcher.common.util.DebugLog

/**
 * 接听 / 挂断 来电的业务封装。
 *
 * 之所以从 [IncomingCallActivity] 抽出，是因为它涉及：
 * - 平台 SDK 兼容（[IncomingPlatformCompat]）
 * - 权限检查（ANSWER_PHONE_CALLS）
 * - 多种 fallback（HEADSETHOOK 广播）
 *
 * 这些与 UI 完全正交，单独成类后可针对各平台分支写单元测试。
 */
internal class IncomingCallActions(
    private val context: Context,
    private val platformCompat: IncomingPlatformCompat = IncomingPlatformCompat()
) {
    companion object {
        private const val TAG = "IncomingCallActions"
    }

    data class Result(
        val success: Boolean,
        val detail: String,
        val failureReason: IncomingCallFailureReason? = null
    )

    /**
     * 尝试接听当前响铃中的来电。
     *
     * @param permissionMissingDetail 当 ANSWER_PHONE_CALLS 权限缺失时使用的提示文案
     * @param successDetail 成功时的提示文案
     * @param actionFailureFormat 失败模板（参数：动作名 + 错误描述）
     * @param actionLabel 用于失败模板里的动作名（"接听"）
     * @param unknownErrorLabel 错误描述的兜底
     */
    fun acceptRingingCall(
        permissionMissingDetail: String,
        successDetail: String,
        actionFailureFormat: (action: String, message: String) -> String,
        actionLabel: String,
        unknownErrorLabel: String
    ): Result {
        return try {
            if (platformCompat.supportsAcceptRingingCall) {
                if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    DebugLog.w(TAG, "acceptRingingCall: ANSWER_PHONE_CALLS permission missing")
                    return Result(
                        success = false,
                        detail = permissionMissingDetail,
                        failureReason = IncomingCallFailureReason(
                            IncomingCallFailureCategory.PhonePermission,
                            permissionMissingDetail
                        )
                    )
                }
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.acceptRingingCall()
                DebugLog.i(TAG) { "acceptRingingCall: telecomManager.acceptRingingCall() called" }
            } else {
                sendHookBroadcast()
                DebugLog.i(TAG) { "acceptRingingCall: HEADSETHOOK broadcast sent" }
            }
            Result(success = true, detail = successDetail)
        } catch (throwable: Throwable) {
            DebugLog.e(TAG, "acceptRingingCall: FAILED - ${throwable::class.simpleName}: ${throwable.message}")
            Result(
                success = false,
                detail = actionFailureFormat(
                    actionLabel,
                    throwable.message ?: unknownErrorLabel
                ),
                failureReason = IncomingCallFailureReason(
                    IncomingCallFailureCategory.CallAction,
                    throwable.message ?: throwable.javaClass.simpleName
                )
            )
        }
    }

    /**
     * 尝试挂断当前响铃中的来电。
     */
    @Suppress("DEPRECATION")
    fun endRingingCall(
        unsupportedDetail: String,
        permissionMissingDetail: String,
        successDetail: String,
        actionFailureFormat: (action: String, message: String) -> String,
        actionLabel: String,
        unknownErrorLabel: String
    ): Result {
        return try {
            if (!platformCompat.supportsEndCall) {
                return Result(
                    success = false,
                    detail = unsupportedDetail,
                    failureReason = IncomingCallFailureReason(
                        IncomingCallFailureCategory.UnsupportedPlatform,
                        unsupportedDetail
                    )
                )
            }
            if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                DebugLog.w(TAG, "endRingingCall: ANSWER_PHONE_CALLS permission missing")
                return Result(
                    success = false,
                    detail = permissionMissingDetail,
                    failureReason = IncomingCallFailureReason(
                        IncomingCallFailureCategory.PhonePermission,
                        permissionMissingDetail
                    )
                )
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
            Result(success = true, detail = successDetail)
        } catch (throwable: Throwable) {
            DebugLog.e(TAG, "endRingingCall: FAILED - ${throwable::class.simpleName}: ${throwable.message}")
            Result(
                success = false,
                detail = actionFailureFormat(
                    actionLabel,
                    throwable.message ?: unknownErrorLabel
                ),
                failureReason = IncomingCallFailureReason(
                    IncomingCallFailureCategory.CallAction,
                    throwable.message ?: throwable.javaClass.simpleName
                )
            )
        }
    }

    private fun sendHookBroadcast() {
        context.sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            )
        }, null)
        context.sendOrderedBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            )
        }, null)
    }
}
