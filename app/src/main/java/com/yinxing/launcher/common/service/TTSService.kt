package com.yinxing.launcher.common.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSService(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false
    private val pendingMessages = mutableListOf<String>()

    fun initialize(onReady: (() -> Unit)? = null) {
        if (isInitialized) {
            onReady?.invoke()
            return
        }

        if (isInitializing) {
            return
        }
        isInitializing = true

        tts = TextToSpeech(appContext) { status ->
            isInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                tts?.setSpeechRate(0.8f)
                tts?.setPitch(1.0f)
                isInitialized = true
                synchronized(pendingMessages) {
                    pendingMessages.forEach { speak(it) }
                    pendingMessages.clear()
                }
                onReady?.invoke()
            }
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isInitialized) {
            synchronized(pendingMessages) {
                pendingMessages.add(text)
            }
            return
        }

        tts?.speak(text, queueMode, null, System.currentTimeMillis().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isInitializing = false
    }
}
