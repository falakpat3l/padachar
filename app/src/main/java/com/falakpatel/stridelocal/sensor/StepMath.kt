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

/**
 * Splits [delta] steps taken between [lastMs] and [nowMs] across local midnight, in
 * proportion to the time spent on each side. Gaps over 2 h (or no previous reading) put
 * everything on the current day, because we cannot know when those steps happened.
 * @return list of (epochDay, steps)
 */
fun splitAcrossMidnight(delta: Long, lastMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Long, Long>> {
    val today = DayClock.epochDay(nowMs, zone)
    val gap = nowMs - lastMs
    if (lastMs <= 0 || gap <= 0 || gap > 2 * 3_600_000L || DayClock.epochDay(lastMs, zone) != today - 1) {
        return listOf(today to delta)
    }
    val midnight = LocalDate.ofEpochDay(today).atStartOfDay(zone).toInstant().toEpochMilli()
    val before = delta * (midnight - lastMs) / gap
    return listOf(today - 1 to before, today to delta - before).filter { it.second > 0 }
}

enum class Pace { IDLE, WALK, RUN }

/** Steps per minute at or above this count as running (brisk walking tops out around 130 to 140). */
const val RUN_STEPS_PER_MIN = 145

fun paceFor(stepsInMinute: Int): Pace = when {
    stepsInMinute >= RUN_STEPS_PER_MIN -> Pace.RUN
    stepsInMinute >= 40 -> Pace.WALK
    else -> Pace.IDLE // a few steps around the house is not an active minute
}

/**
 * Groups single-step timestamps (from TYPE_STEP_DETECTOR) into whole clock minutes.
 * The sensor delivers steps in batches, sometimes late, so a minute is only handed out once
 * it is [slackMinutes] old. That way a batch arriving a bit late still lands in its own minute.
 */
class PaceTracker(private val slackMinutes: Long = 2) {
    private val counts = java.util.TreeMap<Long, Int>()

    fun onStep(wallMs: Long) {
        val k = wallMs / 60_000
        counts[k] = (counts[k] ?: 0) + 1
    }

    /** @return finished minutes as (minute start in ms, steps in that minute). */
    fun drain(nowMs: Long, all: Boolean = false): List<Pair<Long, Int>> {
        val limit = nowMs / 60_000 - slackMinutes
        val ready = counts.headMap(if (all) Long.MAX_VALUE else limit).entries.map { it.key * 60_000 to it.value }
        ready.forEach { counts.remove(it.first / 60_000) }
        return ready
    }
}
