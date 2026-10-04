package com.falakpatel.stridelocal.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.Snapshot
import com.falakpatel.stridelocal.data.TrackerState
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Low-power foreground service that keeps the step sensor registered.
 *
 * Threading rule: every field below is read and written on the MAIN thread only
 * (hardware counter callbacks, the date receiver and the flush loop all run there).
 * Accelerometer samples are processed on a background HandlerThread and only the
 * resulting step counts are posted to main. Disk writes go through the repository queue.
 */
class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val ACTION_REFRESH = "com.falakpatel.stridelocal.REFRESH"
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 1
        private const val FLUSH_INTERVAL_MS = 10_000L
        private const val WIDGET_INTERVAL_MS = 30_000L
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

        /** Ask a running service to write its latest numbers and refresh the widget now. */
        fun requestRefresh(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(Intent(context, StepCounterService::class.java).setAction(ACTION_REFRESH))
            }
        }
    }

    private enum class Mode { HARDWARE_COUNTER, ACCELEROMETER, NO_SENSOR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager

    private var mode = Mode.NO_SENSOR
    private var state: TrackerState? = null
    private var today: DailySteps? = null
    private var profile = UserProfile()
    private val cadence = CadenceEstimator()
    private var dirty = false
    private var lastWidgetPush = 0L
    private var lastNotifiedSteps = -1L

    private var accelThread: HandlerThread? = null
    private var accelDetector: AccelStepDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Settings.Global.BOOT_COUNT increases on every boot. Constant for this process. */
    private val bootCount: Int by lazy {
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, StepDelta.UNKNOWN_BOOT)
    }

    /** Midnight, manual clock change or timezone change: close the old day immediately. */
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            rollOverIfNeeded(DayClock.epochDay(System.currentTimeMillis()))
            flush(force = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createChannel()
        ContextCompat.registerReceiver(
            this, dateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val app = strideApp
        scope.launch {
            profile = app.userPreferences.profile.first()
            launch { app.userPreferences.profile.collect { profile = it } }

            // Load the persisted baseline BEFORE listening, so the first event computes a real delta.
            state = app.stepRepository.loadState()
            val day = DayClock.today()
            today = app.stepRepository.getDay(day) ?: DailySteps(day, goal = profile.dailyGoal)
            updateNotification()
            registerSensors()

            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                rollOverIfNeeded(DayClock.today())
                flush(force = false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called on every start (including START_STICKY restarts with a null intent).
        if (!enterForeground()) return START_NOT_STICKY
        if (intent?.action == ACTION_REFRESH) flush(force = true)
        return START_STICKY
    }

    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (e: Exception) {
        // Permission revoked or start not allowed. Stop cleanly instead of crashing.
        stopSelf()
        false
    }

    // ---------------------------------------------------------------- sensors

    @SuppressLint("WakelockTimeout")
    private fun registerSensors() {
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (counter != null) {
            // Hardware counter: the chip counts steps itself, the CPU can sleep.
            // SENSOR_DELAY_NORMAL with no batching flag: events that pile up while the
            // phone sleeps may be dropped, which is harmless because the value is cumulative.
            mode = Mode.HARDWARE_COUNTER
            sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL)
            return
        }

        // Fallback: run our own detector on raw accelerometer data.
        val wakeUp = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        val accel = wakeUp ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            mode = Mode.NO_SENSOR
            updateNotification()
            return
        }
        mode = Mode.ACCELEROMETER
        accelDetector = AccelStepDetector()
        val thread = HandlerThread("accel-steps").also { it.start() }
        accelThread = thread

        // Battery: a wake-up accelerometer with a hardware FIFO lets the CPU sleep and wake
        // only to drain the batch. Without one we must hold a partial wake lock, otherwise
        // samples (and therefore steps) are lost while the screen is off.
        val latencyUs = if (accel.isWakeUpSensor && accel.fifoMaxEventCount > 0) {
            val fifoUs = accel.fifoMaxEventCount.toLong() * ACCEL_PERIOD_US
            (fifoUs * 8 / 10).coerceAtMost(30_000_000L).toInt()
        } else {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StrideLocal:accelSteps")
                .also { it.acquire() }
            0
        }
        sensorManager.registerListener(this, accel, ACCEL_PERIOD_US, latencyUs, Handler(thread.looper))
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> onCounter(event.values[0].toLong(), eventWallTime(event))
            Sensor.TYPE_ACCELEROMETER -> {
                // Runs on the accelerometer thread.
                val wall = eventWallTime(event)
                val steps = accelDetector?.onSample(wall, event.values[0], event.values[1], event.values[2]) ?: 0
                if (steps > 0) mainHandler.post { addSteps(steps.toLong(), wall) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * SensorEvent.timestamp is nanoseconds on the elapsedRealtime clock. Batched events can
     * arrive late, so convert to wall-clock time to put steps on the right calendar day.
     */
    private fun eventWallTime(event: SensorEvent): Long {
        val now = System.currentTimeMillis()
        val ageMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
        return if (ageMs in 0..86_400_000L) now - ageMs else now
    }

    /** Hardware path: raw cumulative counter -> delta -> today's totals. */
    private fun onCounter(counter: Long, wallMs: Long) {
        val s = state ?: return // baseline not loaded yet; the next event will catch up
        val delta = StepDelta.compute(s.lastCounter.takeIf { it >= 0 }, s.bootCount, counter, bootCount)
        state = s.copy(lastCounter = counter, bootCount = bootCount)
        dirty = true // the baseline itself changed, even when delta is 0
        if (delta > 0) addSteps(delta, wallMs)
    }

    private fun addSteps(delta: Long, wallMs: Long) {
        // Day changes only move forward. A late batch from before midnight that arrives after
        // the rollover is counted on the new day (rare, and at most a few seconds of steps).
        rollOverIfNeeded(DayClock.epochDay(wallMs))
        val d = today ?: return
        val spm = cadence.add(delta, wallMs)
        val stride = profile.strideM
        today = d.copy(
            steps = d.steps + delta,
            distanceKm = d.distanceKm + HealthMetrics.distanceKm(delta, stride),
            activeKcal = d.activeKcal + HealthMetrics.kcalForSteps(delta, spm, stride, profile.weightKg),
            goal = profile.dailyGoal,
        )
        dirty = true
    }

    /** Closes the stored day and starts a fresh zero row when the calendar day moves forward. */
    private fun rollOverIfNeeded(day: Long) {
        val d = today ?: return
        if (day <= d.epochDay) return
        val fresh = DailySteps(day, goal = profile.dailyGoal)
        today = fresh
        state?.let { strideApp.stepRepository.enqueue(Snapshot(it, listOf(d, fresh), refreshWidget = true)) }
        lastWidgetPush = SystemClock.elapsedRealtime()
        updateNotification()
    }

    // ---------------------------------------------------------------- persistence

    private fun flush(force: Boolean, notify: Boolean = true) {
        val s = state ?: return
        val d = today ?: return
        if (!dirty && !force) return
        dirty = false
        val now = SystemClock.elapsedRealtime()
        val pushWidget = force || now - lastWidgetPush >= WIDGET_INTERVAL_MS
        if (pushWidget) lastWidgetPush = now
        strideApp.stepRepository.enqueue(Snapshot(s, listOf(d), refreshWidget = pushWidget))
        if (notify) updateNotification()
    }

    override fun onDestroy() {
        isRunning = false
        sensorManager.unregisterListener(this)
        runCatching { unregisterReceiver(dateReceiver) }
        flush(force = true, notify = false) // final write is queued on the app scope, which outlives this service
        accelThread?.quitSafely()
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notification

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val d = today
        val text = when {
            mode == Mode.NO_SENSOR && d != null -> "No step sensor found on this phone"
            d == null -> "Starting step tracking"
            else -> String.format(Locale.getDefault(), "%,d steps  |  %.2f km", d.steps, d.distanceKm)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle("StrideLocal")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        val steps = today?.steps ?: -1
        if (steps == lastNotifiedSteps && mode != Mode.NO_SENSOR) return
        lastNotifiedSteps = steps
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }
}
