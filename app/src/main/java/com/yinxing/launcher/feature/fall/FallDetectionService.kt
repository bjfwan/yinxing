package com.yinxing.launcher.feature.fall

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterUsageEvents
import com.yinxing.launcher.data.home.LauncherPreferences

class FallDetectionService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private val engine = FallDetectionEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var accelerometer: Sensor? = null
    private var sensorRegistered = false
    private var alertActive = false
    private var cooldownUntilElapsedMs = 0L
    private var lastCallAttemptElapsedMs = 0L

    private val automaticCall = Runnable { callFamilyIfNeeded() }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ensureNotificationChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_FALSE_ALARM -> resolveAlert(falseAlarm = true)
            ACTION_CALL_FAMILY -> callFamilyIfNeeded()
            else -> startMonitoring()
        }
        return if (LauncherPreferences.getInstance(this).isFallDetectionEnabled()) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || alertActive) return
        if (SystemClock.elapsedRealtime() < cooldownUntilElapsedMs) return
        val detectionContext = if (FallCallTransitionContext.isActive()) {
            FallDetectionContext.CallTransition
        } else {
            FallDetectionContext.Normal
        }
        if (engine.accept(
                event.timestamp,
                event.values[0],
                event.values[1],
                event.values[2],
                detectionContext
            ) ==
            FallDetectionEvent.PossibleFall
        ) {
            beginAlert()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handler.removeCallbacks(automaticCall)
        unregisterSensor()
        super.onDestroy()
    }

    private fun startMonitoring() {
        val preferences = LauncherPreferences.getInstance(this)
        if (!preferences.isFallDetectionEnabled() || preferences.getFallEmergencyContact().isEmpty()) {
            stopMonitoring()
            return
        }

        val notification = FallDetectionNotificationController.buildMonitoring(
            this,
            sensorAvailable = accelerometer != null
        )
        ServiceCompat.startForeground(
            this,
            FallDetectionNotificationController.MONITORING_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            }
        )

        if (accelerometer == null) {
            LobsterClient.reportUsage(this, LobsterUsageEvents.FALL_SENSOR_UNAVAILABLE)
            preferences.setFallDetectionEnabled(false)
            stopSelf()
            return
        }
        registerSensor()
        LobsterClient.reportUsage(this, LobsterUsageEvents.FALL_DETECTION_STARTED)
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(automaticCall)
        unregisterSensor()
        FallDetectionNotificationController.cancelAlert(this)
        FallDetectionNotificationController.cancelMonitoring(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerSensor() {
        if (sensorRegistered || alertActive) return
        val sensor = accelerometer ?: return
        sensorRegistered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager.unregisterListener(this)
        sensorRegistered = false
    }

    private fun beginAlert() {
        if (alertActive) return
        alertActive = true
        lastCallAttemptElapsedMs = 0L
        unregisterSensor()
        FallDetectionNotificationController.showAlert(this)
        LobsterClient.reportUsage(this, LobsterUsageEvents.FALL_POSSIBLE_DETECTED)
        handler.removeCallbacks(automaticCall)
        handler.postDelayed(automaticCall, AUTO_CALL_DELAY_MS)
    }

    private fun callFamilyIfNeeded() {
        if (!alertActive) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallAttemptElapsedMs < MINIMUM_CALL_RETRY_INTERVAL_MS) return
        lastCallAttemptElapsedMs = now
        val number = LauncherPreferences.getInstance(this).getFallEmergencyContact()
        val called = number.isNotEmpty() && FallEmergencyCaller.placeFamilyCall(this, number)
        LobsterClient.reportUsage(
            this,
            if (called) LobsterUsageEvents.FALL_FAMILY_CALL_STARTED
            else LobsterUsageEvents.FALL_FAMILY_CALL_FAILED
        )
        if (called) resolveAlert(falseAlarm = false)
    }

    private fun resolveAlert(falseAlarm: Boolean) {
        if (!alertActive) return
        handler.removeCallbacks(automaticCall)
        alertActive = false
        lastCallAttemptElapsedMs = 0L
        cooldownUntilElapsedMs = SystemClock.elapsedRealtime() + POST_ALERT_COOLDOWN_MS
        engine.reset()
        FallDetectionNotificationController.cancelAlert(this)
        if (falseAlarm) {
            LobsterClient.reportUsage(this, LobsterUsageEvents.FALL_ALERT_CANCELLED)
        }
        registerSensor()
    }

    companion object {
        internal const val ACTION_START = "com.yinxing.launcher.action.START_FALL_DETECTION"
        internal const val ACTION_STOP = "com.yinxing.launcher.action.STOP_FALL_DETECTION"
        internal const val ACTION_FALSE_ALARM = "com.yinxing.launcher.action.FALL_FALSE_ALARM"
        internal const val ACTION_CALL_FAMILY = "com.yinxing.launcher.action.FALL_CALL_FAMILY"
        private const val AUTO_CALL_DELAY_MS = 30_000L
        private const val POST_ALERT_COOLDOWN_MS = 5 * 60_000L
        private const val MINIMUM_CALL_RETRY_INTERVAL_MS = 5_000L

        fun reconcile(context: Context) {
            val preferences = LauncherPreferences.getInstance(context)
            if (preferences.isFallDetectionEnabled() &&
                preferences.getFallEmergencyContact().isNotEmpty()
            ) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, FallDetectionService::class.java).setAction(ACTION_START)
                    )
                }.onFailure {
                    LobsterClient.log("[跌倒检测] 前台服务启动失败: ${it.javaClass.simpleName}")
                }
            } else {
                context.stopService(Intent(context, FallDetectionService::class.java))
                FallDetectionNotificationController.cancelMonitoring(context)
                FallDetectionNotificationController.cancelAlert(context)
            }
        }

        fun resolveFalseAlarm(context: Context) = sendAction(context, ACTION_FALSE_ALARM)

        fun callFamilyNow(context: Context) = sendAction(context, ACTION_CALL_FAMILY)

        fun ensureNotificationChannels(context: Context) {
            FallDetectionNotificationController.ensureChannels(context)
        }

        private fun sendAction(context: Context, action: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FallDetectionService::class.java).setAction(action)
                )
            }.onFailure {
                LobsterClient.log("[跌倒检测] 处理求助操作失败: ${it.javaClass.simpleName}")
            }
        }
    }
}
