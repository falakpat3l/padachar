package com.falakpatel.stridelocal.health

import java.util.Locale
import kotlin.math.floor

/**
 * Steps against the daily goal, for Home, the widget, the charts and the goal notice.
 * Unlike [DayRings], nothing stops at 100%, so walking past the goal still shows.
 * Pure Kotlin, unit tested.
 */
object GoalProgress {
    fun reached(steps: Long, goal: Int): Boolean = goal > 0 && steps >= goal

    /** Whole percent, rounded down, so 7,999 of 8,000 is 99% and 100% only shows once you are there. */
    fun percent(steps: Long, goal: Int): Int =
        if (goal > 0 && steps > 0) (steps * 100 / goal).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0

    /** The same for decimals such as km. The tiny extra stops 0.29 / 1.0 from becoming 28%. */
    fun percentOf(value: Double, target: Double): Int =
        if (target > 0 && value > 0) floor(value / target * 100 + 1e-9).toInt() else 0

    /** "+1,240 over your goal", "Right on your goal", or "" before the goal is reached. */
    fun overText(steps: Long, goal: Int, locale: Locale = Locale.getDefault()): String = when {
        !reached(steps, goal) -> ""
        steps == goal.toLong() -> "Right on your goal"
        else -> String.format(locale, "+%,d over your goal", steps - goal)
    }

    /** For the chart caption: "Goal reached on 3 days." or "" when no day reached it. */
    fun daysReachedText(steps: List<Long>, goal: Int): String =
        when (val n = steps.count { reached(it, goal) }) {
            0 -> ""
            1 -> "Goal reached on 1 day."
            else -> "Goal reached on $n days."
        }
}
