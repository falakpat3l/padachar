package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.health.BmiCategory
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.health.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMetricsTest {
    @Test fun bmi() {
        val bmi = HealthMetrics.bmi(70.0, 175.0)
        assertEquals(22.86, bmi, 0.01)
        assertEquals(BmiCategory.NORMAL, HealthMetrics.bmiCategory(bmi))
        assertEquals(BmiCategory.OBESE, HealthMetrics.bmiCategory(31.0))
    }

    @Test fun stride() = assertEquals(0.7245, HealthMetrics.strideMeters(175.0), 1e-4)

    @Test fun bmrMifflin() {
        assertEquals(1673.75, HealthMetrics.bmrMifflinStJeor(70.0, 175.0, 25, Sex.MALE), 0.01)
        assertEquals(1507.75, HealthMetrics.bmrMifflinStJeor(70.0, 175.0, 25, Sex.FEMALE), 0.01)
    }

    @Test fun distance() = assertEquals(7.245, HealthMetrics.distanceKm(10_000, 0.7245), 1e-6)

    @Test fun metInterpolation() {
        assertEquals(3.5, HealthMetrics.metForSpeed(4.8), 1e-9)
        assertEquals(3.9, HealthMetrics.metForSpeed(5.2), 1e-9)
        assertEquals(11.8, HealthMetrics.metForSpeed(20.0), 1e-9)
    }

    @Test fun kcalForTenThousandSteps() {
        // 70 kg, 0.72 m stride, 110 spm (about 4.75 km/h): expect roughly 250 to 350 active kcal
        val kcal = HealthMetrics.kcalForSteps(10_000, 110.0, 0.72, 70.0)
        assertTrue("got $kcal", kcal in 250.0..350.0)
    }
}
