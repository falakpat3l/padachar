package com.falakpatel.stridelocal.health

import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.UserProfile

/**
 * The three rings shown on Home and in the rings widget, each from 0 to 1:
 *   steps    - steps today / daily step goal
 *   distance - km today / the km your step goal covers (goal x stride)
 *   burned - active kcal burned today / kcal the step goal would burn
 *   eaten  - kcal eaten today / your food goal (or, with no goal set, kcal used today: BMR + active)
 */
data class DayRings(
    val steps: Float,
    val distance: Float,
    val distanceGoalKm: Double,
    val burned: Float,
    val eaten: Float,
    val burnGoalKcal: Double,
    val eatTargetKcal: Double,
) {
    companion object {
        fun of(day: DailySteps, profile: UserProfile, eatenKcal: Double): DayRings {
            val goal = profile.dailyGoal
            val burnGoal = HealthMetrics.kcalForSteps(goal.toLong(), 100.0, profile.strideM, profile.weightKg)
            val eatTarget = if (profile.foodGoalKcal > 0) profile.foodGoalKcal.toDouble() else profile.bmr + day.activeKcal
            val distanceGoal = goal * profile.strideM / 1000.0
            return DayRings(
                steps = fraction(day.steps.toDouble(), goal.toDouble()),
                distance = fraction(day.distanceKm, distanceGoal),
                distanceGoalKm = distanceGoal,
                burned = fraction(day.activeKcal, burnGoal),
                eaten = fraction(eatenKcal, eatTarget),
                burnGoalKcal = burnGoal,
                eatTargetKcal = eatTarget,
            )
        }

        private fun fraction(value: Double, target: Double): Float =
            if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    }
}
