package com.bajianfeng.launcher.common.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSService private constructor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false
    private val pendingMessages = mutableListOf<String>()

    companion object {
        @Volatile
        private var instance: TTSService? = null

        fun getInstance(context: Context): TTSService {
            return instance ?: synchronized(this) {
                instance ?: TTSService(context.applicationContext).also { instance = it }
            }
        }
    }

    fun initialize(onReady: (() -> Unit)? = null) {
        if (isInitialized) {
            onReady?.invoke()
            return
        }

        if (isInitializing) return
        isInitializing = true

        tts = TextToSpeech(context) { status ->
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
        instance = null
    }
}
