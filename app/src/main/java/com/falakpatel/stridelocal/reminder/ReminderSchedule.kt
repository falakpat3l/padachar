package com.falakpatel.stridelocal.reminder

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * When to nudge. Pure Kotlin (no Android), so it is unit tested.
 * Reminders come at random times roughly every 1.5 hours (75 to 105 minutes apart),
 * only between 7:00 and 21:00, and are skipped if you already walked since the last one.
 */
object ReminderSchedule {
    const val START_HOUR = 7
    const val END_HOUR = 21
    const val MIN_GAP_MIN = 75
    const val MAX_GAP_MIN = 105
    /** Steps since the last check that count as "already moving", so no nudge is needed. */
    const val ACTIVE_STEPS = 300

    fun inWindow(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(ms).atZone(zone).hour in START_HOUR until END_HOUR

    /** Next reminder time: now + 75 to 105 min, or a random time in 7:00 to 8:00 next morning. */
    fun next(nowMs: Long, random: Random = Random.Default, zone: ZoneId = ZoneId.systemDefault()): Long {
        val candidate = nowMs + (MIN_GAP_MIN + random.nextInt(MAX_GAP_MIN - MIN_GAP_MIN + 1)) * 60_000L
        val z = Instant.ofEpochMilli(candidate).atZone(zone)
        if (z.hour in START_HOUR until END_HOUR) return candidate
        val day = if (z.hour >= END_HOUR) z.toLocalDate().plusDays(1) else z.toLocalDate()
        return day.atTime(START_HOUR, 0).atZone(zone).toInstant().toEpochMilli() + random.nextInt(61) * 60_000L
    }

    /** First check of the day always reminds; later ones only if you walked less than [ACTIVE_STEPS]. */
    fun shouldRemind(stepsNow: Long, stepsAtLastCheck: Long, sameDay: Boolean): Boolean =
        !sameDay || stepsNow - stepsAtLastCheck < ACTIVE_STEPS
}
