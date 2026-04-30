package com.yinxing.launcher.feature.incoming

import android.Manifest
import android.content.Context
import android.provider.CallLog
import androidx.core.content.ContextCompat

enum class IncomingCallRiskLevel {
    Low,
    Medium,
    High
}

data class IncomingCallRiskAssessment(
    val level: IncomingCallRiskLevel,
    val label: String,
    val shouldSilence: Boolean,
    val confidence: Float,
    val reason: String
) {
    val shouldShowWarning: Boolean
        get() = level != IncomingCallRiskLevel.Low && label.isNotBlank()
}

data class IncomingCallLocalRiskSignal(
    val label: String,
    val score: Float
)

object IncomingCallRiskHeuristics {
    fun evaluate(rawNumber: String, recentSameNumberCount: Int, hourOfDay: Int): IncomingCallLocalRiskSignal {
        val digits = rawNumber.filter(Char::isDigit)
        return when {
            digits.isBlank() -> IncomingCallLocalRiskSignal("missing_number", 0.82f)
            recentSameNumberCount >= 3 -> IncomingCallLocalRiskSignal("repeated_unknown", 0.72f)
            digits.length in 3..5 -> IncomingCallLocalRiskSignal("short_code", 0.68f)
            digits.startsWith("95") && digits.length >= 5 -> IncomingCallLocalRiskSignal("service_hotline", 0.6f)
            digits.startsWith("400") && digits.length >= 7 -> IncomingCallLocalRiskSignal("enterprise_hotline", 0.56f)
            rawNumber.trim().startsWith("+") || digits.startsWith("00") -> IncomingCallLocalRiskSignal("overseas_or_masked", 0.64f)
            hourOfDay <= 7 || hourOfDay >= 22 -> IncomingCallLocalRiskSignal("late_hour_unknown", 0.58f)
            else -> IncomingCallLocalRiskSignal("unknown_number", 0.32f)
        }
    }
}

class IncomingCallRiskAssessor(
    context: Context,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext

    fun assess(incomingNumber: String, knownContact: Boolean): IncomingCallRiskAssessment? {
        if (knownContact || incomingNumber.isBlank()) {
            return null
        }
        val recentSameNumberCount = readRecentSameNumberCount(incomingNumber)
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowProvider() }
        val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val localSignal = IncomingCallRiskHeuristics.evaluate(
            rawNumber = incomingNumber,
            recentSameNumberCount = recentSameNumberCount,
            hourOfDay = hourOfDay
        )
        val level = when {
            localSignal.score >= 0.7f -> IncomingCallRiskLevel.High
            localSignal.score >= 0.56f -> IncomingCallRiskLevel.Medium
            else -> IncomingCallRiskLevel.Low
        }
        val label = when (level) {
            IncomingCallRiskLevel.High -> "疑似骚扰电话"
            IncomingCallRiskLevel.Medium -> "来电风险提醒"
            IncomingCallRiskLevel.Low -> ""
        }
        val reason = when (localSignal.label) {
            "missing_number" -> "号码异常"
            "repeated_unknown" -> "近期重复来电"
            "short_code" -> "短号来电"
            "service_hotline" -> "客服热线来电"
            "enterprise_hotline" -> "企业热线来电"
            "overseas_or_masked" -> "疑似境外或隐藏号码"
            "late_hour_unknown" -> "夜间陌生来电"
            else -> "陌生号码"
        }
        return IncomingCallRiskAssessment(
            level = level,
            label = label,
            shouldSilence = level == IncomingCallRiskLevel.High,
            confidence = localSignal.score.coerceIn(0f, 1f),
            reason = reason
        )
    }

    private fun readRecentSameNumberCount(incomingNumber: String): Int {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }
        val incomingDigits = incomingNumber.filter(Char::isDigit)
        if (incomingDigits.isBlank()) {
            return 0
        }
        val threshold = nowProvider() - 7L * 24 * 60 * 60 * 1000
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE)
        return runCatching {
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                var count = 0
                var scanned = 0
                while (cursor.moveToNext() && scanned < 40) {
                    scanned++
                    val date = cursor.getLong(dateIndex)
                    if (date < threshold) {
                        break
                    }
                    val number = cursor.getString(numberIndex).orEmpty().filter(Char::isDigit)
                    if (number.isNotBlank() && number == incomingDigits) {
                        count++
                    }
                }
                count
            } ?: 0
        }.getOrDefault(0)
    }
}
