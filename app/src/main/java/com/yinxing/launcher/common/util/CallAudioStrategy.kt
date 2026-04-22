package com.yinxing.launcher.common.util

import android.content.Context
import android.media.AudioManager
import android.util.Log

object CallAudioStrategy {

    private const val TAG = "CallAudioStrategy"

    data class Result(
        val speakerEnabled: Boolean,
        val keptPrivateOutput: Boolean
    )

    fun maximizeIncomingRingVolume(context: Context) {
        val audioManager = audioManager(context) ?: return
        runCatching {
            setStreamToMax(audioManager, AudioManager.STREAM_RING)
            Log.i(TAG, "maximizeIncomingRingVolume: ring volume=max")
        }.onFailure { throwable ->
            Log.e(
                TAG,
                "maximizeIncomingRingVolume: FAILED - ${throwable::class.simpleName}: ${throwable.message}"
            )
        }
    }

    fun prepareSystemCall(context: Context): Result {
        val audioManager = audioManager(context) ?: return Result(false, false)
        return runCatching {
            setStreamToMax(audioManager, AudioManager.STREAM_VOICE_CALL)
            enableSpeakerphone(audioManager, AudioManager.MODE_IN_CALL)
        }.getOrElse { throwable ->
            Log.e(
                TAG,
                "prepareSystemCall: FAILED - ${throwable::class.simpleName}: ${throwable.message}"
            )
            Result(false, false)
        }
    }

    fun prepareVoipCall(context: Context): Result {
        val audioManager = audioManager(context) ?: return Result(false, false)
        return runCatching {
            setStreamToMax(audioManager, AudioManager.STREAM_VOICE_CALL)
            enableSpeakerphone(audioManager, AudioManager.MODE_IN_COMMUNICATION)
        }.getOrElse { throwable ->
            Log.e(
                TAG,
                "prepareVoipCall: FAILED - ${throwable::class.simpleName}: ${throwable.message}"
            )
            Result(false, false)
        }
    }

    private fun audioManager(context: Context): AudioManager? {
        return context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun setStreamToMax(audioManager: AudioManager, stream: Int) {
        if (audioManager.isVolumeFixed) {
            return
        }
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        if (maxVolume > 0) {
            audioManager.setStreamVolume(stream, maxVolume, 0)
        }
    }

    private fun enableSpeakerphone(audioManager: AudioManager, mode: Int): Result {
        @Suppress("DEPRECATION")
        if (audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn) {
            return Result(speakerEnabled = false, keptPrivateOutput = true)
        }
        audioManager.mode = mode
        audioManager.isSpeakerphoneOn = true
        return Result(speakerEnabled = true, keptPrivateOutput = false)
    }
}
