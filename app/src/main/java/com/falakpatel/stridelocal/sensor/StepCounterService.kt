package com.falakpatel.stridelocal.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.strideApp
import com.falakpatel.stridelocal.widget.RingsWidget
import com.falakpatel.stridelocal.widget.StepWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps the step sensor registered in the background.
 *
 * Why a foreground service at all: since Android 9, apps in the background stop receiving
 * sensor events, so a background job could not read the counter. A foreground service is the
 * only reliable way. The app does not ask for notification permission, so on Android 13+ the
 * required notification is hidden (it only appears in the system's "active apps" list).
 *
 * Battery: with a hardware step counter the chip does the counting and the CPU sleeps. We only
 * remember the latest value and hand it to [StepEngine] every 5 s while new steps arrive.
 */
class StepCounterService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 1
        private const val FLUSH_MS = 5_000L
        private const val WIDGET_MS = 30_000L
        private const val ACCEL_PERIOD_US = 20_000 // 50 Hz

        @Volatile
        var isRunning = false
            private set

        fun hasActivityPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

        /** Safe to call from anywhere. Returns false if Android refused the start. */
        fun start(context: Context): Boolean {
            if (!hasActivityPermission(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
                true
            } catch (e: Exception) { // ForegroundServiceStartNotAllowedException, SecurityException
                false
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sensorManager: SensorManager

    // Written on the main thread (counter) or the accel thread (accelSteps, atomic).
    private var pendingCounter = -1L
    private var pendingWallMs = 0L
    private var flushedCounter = -1L
    private val accelSteps = AtomicLong(0)
    private val pace = PaceTracker() // main thread only
    @Volatile private var lastWidgetMs = 0L
    private var lastWidgetDay = -1L

    private var accelThread: HandlerThread? = null
    private var accelDetector: AccelStepDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN),
        )
        registerSensors()
        scope.launch {
            while (isActive) {
                delay(FLUSH_MS) // uptime based: does not wake a sleeping phone
                flush(final = false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = try {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle("Counting steps on this phone")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        START_STICKY
    } catch (e: Exception) { // permission revoked: stop cleanly
        stopSelf()
        START_NOT_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun registerSensors() {
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (counter != null) {
            // Events that pile up while the phone sleeps may be dropped. Harmless: the value is
            // cumulative, so the next event still contains every step.
            sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL)
            // Step detector: one event per step with its own timestamp, batched by the chip
            // (up to 30 s) so the CPU can sleep. Used only to time steps for walk vs run.
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
            }
            return
        }
        // Fallback for phones without a step chip: our own detector on the accelerometer.
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        accelDetector = AccelStepDetector()
        val thread = HandlerThread("accel-steps").also { it.start() }
        accelThread = thread
        // A wake-up sensor with a hardware FIFO lets the CPU sleep between batches. Otherwise a
        // partial wake lock is needed or samples (and steps) are lost with the screen off.
        val latencyUs = if (accel.isWakeUpSensor && accel.fifoMaxEventCount > 0) {
            (accel.fifoMaxEventCount.toLong() * ACCEL_PERIOD_US * 8 / 10).coerceAtMost(30_000_000L).toInt()
        } else {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Padachar:accelSteps").also { it.acquire() }
            0
        }
        sensorManager.registerListener(this, accel, ACCEL_PERIOD_US, latencyUs, Handler(thread.looper))
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> { // main thread
                pendingCounter = event.values[0].toLong()
                pendingWallMs = eventWallTime(event)
            }
            Sensor.TYPE_STEP_DETECTOR -> pace.onStep(eventWallTime(event)) // main thread
            Sensor.TYPE_ACCELEROMETER -> { // accel thread
                val n = accelDetector?.onSample(eventWallTime(event), event.values[0], event.values[1], event.values[2]) ?: 0
                if (n > 0) accelSteps.addAndGet(n.toLong())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** SensorEvent.timestamp uses the elapsedRealtime clock. Convert so late batches land on the right day. */
    private fun eventWallTime(event: SensorEvent): Long {
        val now = System.currentTimeMillis()
        val ageMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
        return if (ageMs in 0..86_400_000L) now - ageMs else now
    }

    private fun flush(final: Boolean) {
        val app = strideApp
        val counter = pendingCounter
        val wall = pendingWallMs
        val accel = accelSteps.getAndSet(0)
        val minutes = pace.drain(System.currentTimeMillis(), all = final)
        val today = DayClock.today()
        val hasCounter = counter >= 0 && counter != flushedCounter
        val newDay = today != lastWidgetDay
        if (!hasCounter && accel == 0L && minutes.isEmpty() && !newDay && !final) return
        if (hasCounter) flushedCounter = counter
        lastWidgetDay = today
        app.appScope.launch {
            if (hasCounter) StepEngine.onCounter(app, counter, wall)
            if (accel > 0) StepEngine.addSteps(app, accel, System.currentTimeMillis())
            StepEngine.addPaceMinutes(app, minutes)
            val now = SystemClock.elapsedRealtime()
            if (final || newDay || now - lastWidgetMs >= WIDGET_MS) {
                lastWidgetMs = now
                runCatching { StepWidget().updateAll(app) }
                runCatching { RingsWidget().updateAll(app) }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        sensorManager.unregisterListener(this)
        flush(final = true) // runs on the app scope, which outlives this service
        accelThread?.quitSafely()
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }
}
