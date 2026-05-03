package com.yinxing.launcher.feature.incoming

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 来电姓名的语音播报。
 *
 * 仅围绕 [IncomingCallActivity] 的需求做最小封装：
 * - 异步初始化 TTS（[TextToSpeech.OnInitListener]）；
 * - 缓存"在 init 完成前到达"的播报内容；
 * - 中文优先，回退到普通中文 locale；
 * - 通过 [AudioManager.STREAM_RING] 流播放，避免被静音模式吞掉。
 *
 * 与 [com.yinxing.launcher.common.service.TTSService] 的差异：
 * 后者面向通用提示音，使用 STREAM_DEFAULT；本类专门服务来电场景，使用 STREAM_RING。
 */
internal class IncomingCallAnnouncer(
    context: Context,
    private val onInit: ((ready: Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = TextToSpeech(appContext, this)
    private var ready: Boolean = false
    private var pendingAnnouncement: String? = null
    private var lastAnnouncedCaller: String? = null

    /** 系统初始化回调。 */
    override fun onInit(status: Int) {
        val engine = textToSpeech ?: return
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            pendingAnnouncement = null
            onInit?.invoke(false)
            return
        }
        val localeResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        ready = localeResult != TextToSpeech.LANG_MISSING_DATA &&
            localeResult != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) {
            val fallbackResult = engine.setLanguage(Locale.CHINESE)
            ready = fallbackResult != TextToSpeech.LANG_MISSING_DATA &&
                fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (!ready) {
            pendingAnnouncement = null
            onInit?.invoke(false)
            return
        }
        engine.setSpeechRate(0.92f)
        engine.setPitch(1.0f)
        flushPending()
        onInit?.invoke(true)
    }

    /**
     * 朗读 [callerName]。同一个 callerName 不会连续重复播报。
     */
    fun announce(callerName: String, voiceText: String) {
        if (callerName == lastAnnouncedCaller) {
            return
        }
        lastAnnouncedCaller = callerName
        pendingAnnouncement = voiceText
        flushPending()
    }

    /** 强制重置 callerName 缓存（用于 [IncomingCallActivity.onNewIntent] 等场景）。 */
    fun reset() {
        lastAnnouncedCaller = null
        pendingAnnouncement = null
    }

    fun stop() {
        pendingAnnouncement = null
        textToSpeech?.stop()
    }

    fun shutdown() {
        pendingAnnouncement = null
        ready = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun flushPending() {
        if (!ready) return
        val announcement = pendingAnnouncement ?: return
        val engine = textToSpeech ?: return
        pendingAnnouncement = null
        engine.stop()
        engine.speak(
            announcement,
            TextToSpeech.QUEUE_FLUSH,
            Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_RING)
            },
            "incoming_call_announcement"
        )
    }
}
