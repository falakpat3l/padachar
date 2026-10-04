package com.falakpatel.stridelocal.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.glance.appwidget.updateAll
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.TrackerState
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.strideApp
import com.falakpatel.stridelocal.widget.StepWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * The single place where steps are written. Everything (service, app screen, widget button)
 * calls in here, and a Mutex makes each read-compute-write atomic, so two callers can never
 * count the same steps twice. All state lives in Room, nothing important is kept in memory.
 */
object StepEngine {
    private val mutex = Mutex()

    private fun bootCount(ctx: Context) =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, StepDelta.UNKNOWN_BOOT)

    /** Hardware path: raw cumulative counter value. Returns the new steps added. */
    suspend fun onCounter(ctx: Context, counter: Long, wallMs: Long): Long = mutex.withLock {
        val repo = ctx.strideApp.stepRepository
        val s = repo.loadState()
        val boot = bootCount(ctx)
        val delta = StepDelta.compute(s.lastCounter.takeIf { it >= 0 }, s.bootCount, counter, boot)
        val rows = if (delta > 0) addToDays(ctx, delta, s.lastSyncMs, wallMs) else emptyList()
        repo.save(TrackerState(lastCounter = counter, bootCount = boot, lastSyncMs = wallMs), rows)
        delta
    }

    /** Accelerometer fallback path: steps already detected. */
    suspend fun addSteps(ctx: Context, steps: Long, wallMs: Long) = mutex.withLock {
        val repo = ctx.strideApp.stepRepository
        val s = repo.loadState()
        repo.save(s.copy(lastSyncMs = wallMs), addToDays(ctx, steps, s.lastSyncMs, wallMs))
    }

    private suspend fun addToDays(ctx: Context, delta: Long, lastMs: Long, nowMs: Long): List<DailySteps> {
        val app = ctx.strideApp
        val p = app.userPreferences.profile.first()
        val minutes = (nowMs - lastMs) / 60_000.0
        // Pace from this batch. Short or unknown gaps fall back to a normal walk (100 steps/min).
        val cadence = if (lastMs > 0 && minutes in 0.05..120.0) max(100.0, delta / minutes) else 100.0
        val today = DayClock.epochDay(nowMs)
        return splitAcrossMidnight(delta, lastMs, nowMs).map { (day, n) ->
            val row = app.stepRepository.getDay(day) ?: DailySteps(day, goal = p.dailyGoal)
            row.copy(
                steps = row.steps + n,
                distanceKm = row.distanceKm + HealthMetrics.distanceKm(n, p.strideM),
                activeKcal = row.activeKcal + HealthMetrics.kcalForSteps(n, cadence, p.strideM, p.weightKg),
                goal = if (day == today) p.dailyGoal else row.goal,
            )
        }
    }
}

object StepSync {
    /**
     * Reads the hardware counter once. Android must deliver the current value as soon as an
     * on-change sensor is activated, so this normally returns within milliseconds.
     */
    suspend fun readCounterOnce(ctx: Context, timeoutMs: Long = 4_000): Long? = withTimeoutOrNull(timeoutMs) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return@withTimeoutOrNull null
        suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    sm.unregisterListener(this)
                    if (cont.isActive) cont.resume(e.values[0].toLong())
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            cont.invokeOnCancellation { sm.unregisterListener(listener) }
        }
    }

    /** Pull the latest count right now (app opened, widget refresh) and refresh the widget. */
    suspend fun syncNow(ctx: Context) {
        if (StepCounterService.hasActivityPermission(ctx)) {
            readCounterOnce(ctx)?.let { StepEngine.onCounter(ctx, it, System.currentTimeMillis()) }
        }
        runCatching { StepWidget().updateAll(ctx) }
    }
}
