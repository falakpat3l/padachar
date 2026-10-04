package com.falakpatel.stridelocal.health

import kotlin.math.max

enum class Sex { MALE, FEMALE }

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("Underweight"), NORMAL("Normal"), OVERWEIGHT("Overweight"), OBESE("Obese")
}

/** Pure offline formulas. No Android imports so everything is unit tested on the JVM. */
object HealthMetrics {

    /** Walking stride estimate: stride = height x 0.414. */
    const val STRIDE_FACTOR = 0.414

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return if (m <= 0.0) 0.0 else weightKg / (m * m)
    }

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.UNDERWEIGHT
        bmi < 25.0 -> BmiCategory.NORMAL
        bmi < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    fun strideMeters(heightCm: Double): Double = heightCm / 100.0 * STRIDE_FACTOR

    /** Mifflin-St Jeor BMR in kcal/day: 10W + 6.25H - 5A + 5 (male) or - 161 (female). */
    fun bmrMifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double =
        10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + if (sex == Sex.MALE) 5.0 else -161.0

    fun distanceKm(steps: Long, strideMeters: Double): Double = steps * strideMeters / 1000.0

    /**
     * MET for a given speed, linearly interpolated from the Adult Compendium of
     * Physical Activities (walking and running entries).
     */
    private val metTable = listOf(
        0.0 to 1.0, 3.2 to 2.0, 4.0 to 3.0, 4.8 to 3.5, 5.6 to 4.3,
        6.4 to 5.0, 7.2 to 7.0, 8.0 to 8.3, 9.7 to 9.8, 11.3 to 11.0, 12.9 to 11.8,
    )

    fun metForSpeed(speedKmh: Double): Double {
        if (speedKmh <= 0.0) return 1.0
        for (i in 1 until metTable.size) {
            val (s1, m1) = metTable[i - 1]
            val (s2, m2) = metTable[i]
            if (speedKmh <= s2) return m1 + (m2 - m1) * (speedKmh - s1) / (s2 - s1)
        }
        return metTable.last().second
    }

    /**
     * Active (net) kcal = (MET - 1) x weight kg x hours.
     * We subtract 1 MET because resting burn is already shown as BMR.
     */
    fun activeKcal(met: Double, weightKg: Double, minutes: Double): Double =
        max(0.0, met - 1.0) * weightKg * minutes / 60.0

    /**
     * Energy for a batch of steps: pace (steps/min) x stride gives speed, speed gives MET,
     * step count / pace gives the time spent.
     */
    fun kcalForSteps(steps: Long, cadenceSpm: Double, strideMeters: Double, weightKg: Double): Double {
        if (steps <= 0) return 0.0
        val cadence = cadenceSpm.coerceIn(60.0, 200.0)
        val speedKmh = cadence * strideMeters * 60.0 / 1000.0
        val minutes = steps / cadence
        return activeKcal(metForSpeed(speedKmh), weightKg, minutes)
    }
}
