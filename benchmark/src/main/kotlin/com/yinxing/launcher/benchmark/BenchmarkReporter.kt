package com.yinxing.launcher.benchmark

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object BenchmarkReporter {
    private const val TAG = "BenchmarkReporter"

    private const val ENDPOINT = "https://log.likeyou.qzz.io/api/metrics"

    fun report(scenario: String, metrics: List<Pair<String, Long>>) {
        if (metrics.isEmpty()) return

        val context = InstrumentationRegistry.getInstrumentation().context
        val deviceId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        } catch (_: Exception) {
            null
        }

        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            val metricsArray = JSONArray()
            for ((name, durationMs) in metrics) {
                metricsArray.put(JSONObject().apply {
                    put("name", name)
                    put("duration_ms", durationMs)
                })
            }

            val body = JSONObject().apply {
                put("device", deviceName)
                put("device_id", deviceId ?: "")
                put("metrics", metricsArray)
            }

            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }

            val status = connection.responseCode
            if (status in 200..299) {
                Log.i(TAG, "Benchmark 上报成功: ${metrics.size} 条 ($scenario)")
            } else {
                Log.w(TAG, "Benchmark 上报失败: HTTP $status ($scenario)")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark 上报异常: ${e.message} ($scenario)")
        }
    }
}
