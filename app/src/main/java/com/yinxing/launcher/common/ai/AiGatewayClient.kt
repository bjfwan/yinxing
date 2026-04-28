package com.yinxing.launcher.common.ai

import android.content.Context
import android.util.Log
import com.yinxing.launcher.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiGatewayClient(
    context: Context,
    private val baseUrl: String = BuildConfig.AI_GATEWAY_BASE_URL.trim().trimEnd('/'),
    private val deviceCredentials: AiDeviceCredentials = AiDeviceCredentials.getInstance(context)
) {
    companion object {
        private const val TAG = "AiGatewayClient"
    }

    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank()
    }

    suspend fun get(path: String): JSONObject? {
        if (!isConfigured()) {
            Log.w(TAG, "skip method=GET path=$path reason=not_configured")
            return null
        }
        return request("GET", path)
    }

    suspend fun post(
        path: String,
        body: JSONObject,
        connectTimeoutMs: Int = 2500,
        readTimeoutMs: Int = 3500
    ): JSONObject? {
        if (!isConfigured()) {
            Log.w(TAG, "skip method=POST path=$path reason=not_configured")
            return null
        }
        return request("POST", path, body, connectTimeoutMs, readTimeoutMs)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        connectTimeoutMs: Int = 2500,
        readTimeoutMs: Int = 3500
    ): JSONObject? {
        return withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "start method=$method path=$path")
                connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    doOutput = body != null
                    setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    applyDeviceHeaders()
                }
                if (body != null) {
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                        it.write(body.toString())
                    }
                }
                val status = connection.responseCode
                val json = readJson(connection, status)
                val elapsed = System.currentTimeMillis() - startedAt
                Log.d(
                    TAG,
                    "done method=$method path=$path code=$status elapsed=${elapsed}ms available=${json?.optBoolean("available")} error=${json?.optString("error").orEmpty()}"
                )
                json
            } catch (error: Exception) {
                val elapsed = System.currentTimeMillis() - startedAt
                Log.w(TAG, "fail method=$method path=$path elapsed=${elapsed}ms error=${error::class.java.simpleName}:${error.message}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun HttpURLConnection.applyDeviceHeaders() {
        val snapshot = deviceCredentials.snapshot()
        setRequestProperty("x-oldlauncher-device-id", snapshot.deviceId)
        setRequestProperty("x-oldlauncher-device-token", snapshot.deviceToken)
    }

    private fun readJson(connection: HttpURLConnection, status: Int): JSONObject? {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return null
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return if (text.isBlank()) null else JSONObject(text)
    }
}
