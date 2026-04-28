package com.yinxing.launcher.common.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID

data class AiDeviceCredentialSnapshot(
    val deviceId: String,
    val deviceToken: String
) {
    val activationCode: String
        get() = "$deviceId:$deviceToken"
}

class AiDeviceCredentials private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    companion object {
        private const val PREFS_NAME = "ai_device_credentials"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private val random = SecureRandom()

        @Volatile
        private var instance: AiDeviceCredentials? = null

        fun getInstance(context: Context): AiDeviceCredentials {
            return instance ?: synchronized(this) {
                instance ?: AiDeviceCredentials(context.applicationContext).also { instance = it }
            }
        }
    }

    fun snapshot(): AiDeviceCredentialSnapshot {
        synchronized(lock) {
            val currentDeviceId = prefs.getString(KEY_DEVICE_ID, null)
            val currentDeviceToken = prefs.getString(KEY_DEVICE_TOKEN, null)
            if (!currentDeviceId.isNullOrBlank() && !currentDeviceToken.isNullOrBlank()) {
                return AiDeviceCredentialSnapshot(currentDeviceId, currentDeviceToken)
            }
            val deviceId = currentDeviceId?.takeIf { it.isNotBlank() } ?: createDeviceId()
            val deviceToken = currentDeviceToken?.takeIf { it.isNotBlank() } ?: createDeviceToken()
            prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_DEVICE_TOKEN, deviceToken)
                .apply()
            return AiDeviceCredentialSnapshot(deviceId, deviceToken)
        }
    }

    internal fun clearForTesting() {
        prefs.edit().clear().commit()
    }

    private fun createDeviceId(): String {
        return "ol-${UUID.randomUUID()}"
    }

    private fun createDeviceToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
