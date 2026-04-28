package com.yinxing.launcher.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

interface WeatherApiClient {
    suspend fun get(url: String): String
}

class HttpWeatherApiClient(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 7_000
) : WeatherApiClient {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }
        try {
            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }
            val body = stream.use { it.bufferedReader().readText() }
            check(responseCode in 200..299) { "HTTP $responseCode" }
            body
        } finally {
            conn.disconnect()
        }
    }
}
