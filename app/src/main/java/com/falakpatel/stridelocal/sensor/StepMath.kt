package com.falakpatel.stridelocal.sensor

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Converts raw TYPE_STEP_COUNTER readings into "new steps since the last reading".
 *
 * The hardware counter is cumulative since the last boot, so we never store it as
 * the step total. We store the last raw value (and the boot it came from) and add
 * only the difference to today. Pure Kotlin, unit tested.
 */
object StepDelta {
    const val UNKNOWN_BOOT = -1

    /** Guard against corrupt readings (for example a HAL glitch reporting a huge jump). */
    const val MAX_PLAUSIBLE_DELTA = 100_000L

    /**
     * @param prevCounter last raw counter we saw, or null if we never saw one
     * @param prevBoot    Settings.Global.BOOT_COUNT when prevCounter was read
     * @param counter     new raw counter value
     * @param boot        current BOOT_COUNT (UNKNOWN_BOOT if unavailable)
     */
    fun compute(prevCounter: Long?, prevBoot: Int, counter: Long, boot: Int): Long {
        // First reading ever: it contains steps from before the app existed. Use as baseline only.
        if (prevCounter == null || prevCounter < 0) return 0

        // Reboot detection. The counter restarts at 0 on boot, so every step since boot is new.
        // BOOT_COUNT catches the case where the user walked more after the reboot than before it
        // (counter >= prevCounter even though it reset). counter < prevCounter catches OEM resets.
        val bootChanged = boot != UNKNOWN_BOOT && prevBoot != UNKNOWN_BOOT && boot != prevBoot
        val rebooted = bootChanged || counter < prevCounter

        val delta = if (rebooted) counter else counter - prevCounter
        return if (delta in 0..MAX_PLAUSIBLE_DELTA) delta else 0
    }
}

/**
 * Rolling walking pace in steps per minute, used to pick a MET value.
 * Measures over windows of at least 10 s. Long gaps (service asleep, batch delivered later)
 * do not tell us the real pace, so we fall back to a typical walking cadence.
 */
class CadenceEstimator(private val defaultSpm: Double = 100.0) {
    private var windowStartMs = -1L
    private var windowSteps = 0L
    var stepsPerMinute = defaultSpm
        private set

    fun add(steps: Long, timeMs: Long): Double {
        if (windowStartMs < 0) { // steps in the first batch happened before the window opened
            windowStartMs = timeMs
            return stepsPerMinute
        }
        windowSteps += steps
        val dt = timeMs - windowStartMs
        if (dt >= 10_000) {
            stepsPerMinute = if (dt > 120_000) defaultSpm else (windowSteps * 60_000.0 / dt).coerceIn(40.0, 220.0)
            windowStartMs = timeMs
            windowSteps = 0
        }
        return stepsPerMinute
    }
}

object DayClock {
    fun epochDay(wallMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(wallMillis).atZone(zone).toLocalDate().toEpochDay()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun millisUntilNextMidnight(zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = java.time.ZonedDateTime.now(zone)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        return java.time.Duration.between(now, next).toMillis()
    }
}
