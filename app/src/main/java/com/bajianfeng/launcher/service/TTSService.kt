package com.bajianfeng.launcher.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TTSService private constructor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
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
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                tts?.setSpeechRate(0.8f)
                tts?.setPitch(1.0f)
                isInitialized = true
                
                pendingMessages.forEach { speak(it) }
                pendingMessages.clear()
                
                onReady?.invoke()
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }
    
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isInitialized) {
            pendingMessages.add(text)
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
        isInitialized = false
        instance = null
    }
}
