package com.yinxing.launcher.feature.incoming

/**
 * 合并 Android 对同一次来电发送的重复 PHONE_STATE 广播。
 *
 * 同时持有 READ_PHONE_STATE 与 READ_CALL_LOG 时，系统可能分别发送“有号码”和“无号码”
 * 两个 RINGING 广播，且顺序不固定。这里始终保留非空号码，并保证同一轮来电只处理一次。
 *
 * Source: https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED
 */
internal class IncomingPhoneStateEventCoalescer {
    companion object {
        private const val RINGING_STATE = "RINGING"
    }

    class Ticket internal constructor(internal val generation: Long)

    data class ClaimedEvent(val incomingNumber: String)

    private var generation = 0L
    private var currentState: String? = null
    private var bestIncomingNumber = ""
    private var claimed = false

    @Synchronized
    fun record(state: String, incomingNumber: String?): Ticket {
        if (state != RINGING_STATE) {
            generation += 1
            currentState = state
            bestIncomingNumber = ""
            claimed = false
            return Ticket(generation)
        }

        if (currentState != RINGING_STATE) {
            generation += 1
            currentState = RINGING_STATE
            bestIncomingNumber = ""
            claimed = false
        }

        incomingNumber?.trim()?.takeIf(String::isNotEmpty)?.let {
            bestIncomingNumber = it
        }
        return Ticket(generation)
    }

    @Synchronized
    fun claim(ticket: Ticket): ClaimedEvent? {
        if (ticket.generation != generation ||
            currentState != RINGING_STATE ||
            claimed
        ) {
            return null
        }
        claimed = true
        return ClaimedEvent(bestIncomingNumber)
    }
}
