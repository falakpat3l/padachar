package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.sensor.AccelStepDetector
import com.falakpatel.stridelocal.sensor.Pace
import com.falakpatel.stridelocal.sensor.PaceTracker
import com.falakpatel.stridelocal.sensor.paceFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SignalTest {
    private fun walk(stepsPerSec: Double, seconds: Int, amp: Double, noise: Double, seed: Int = 1): Int {
        val d = AccelStepDetector()
        val rnd = Random(seed)
        var steps = 0
        for (i in 0 until seconds * 50) {
            val t = i / 50.0
            val n = { (rnd.nextDouble() - 0.5) * 2 * noise }
            val z = 9.81 + amp * sin(2 * PI * stepsPerSec * t) + n()
            steps += d.onSample((t * 1000).toLong(), n().toFloat(), n().toFloat(), z.toFloat())
        }
        return steps
    }

    @Test fun countsWalking() {
        val steps = walk(stepsPerSec = 2.0, seconds = 30, amp = 3.0, noise = 0.3)
        assertTrue("got $steps", steps in 55..62)
    }

    @Test fun ignoresSensorNoise() = assertEquals(0, walk(2.0, 30, amp = 0.0, noise = 0.3))

    @Test fun paceThresholds() {
        assertEquals(Pace.IDLE, paceFor(10))
        assertEquals(Pace.WALK, paceFor(110))
        assertEquals(Pace.WALK, paceFor(140))
        assertEquals(Pace.RUN, paceFor(160))
    }

    @Test fun paceTrackerWaitsForLateSteps() {
        val t = PaceTracker(slackMinutes = 2)
        repeat(160) { t.onStep(60_000L + it * 375L) } // 160 steps inside minute 1
        assertTrue(t.drain(4 * 60_000L - 1).isEmpty()) // minute ended at 2:00, wait 2 more minutes
        assertEquals(listOf(60_000L to 160), t.drain(4 * 60_000L + 1))
        assertTrue(t.drain(10 * 60_000L).isEmpty())
    }

    @Test fun runningAddsDistanceAndKcal() {
        val (km, kcal) = HealthMetrics.runningExtra(160, 0.72, 70.0)
        assertTrue("km $km", km in 0.04..0.07)     // longer stride over 160 steps
        assertTrue("kcal $kcal", kcal in 2.0..6.0) // running burns about 10 kcal/min at 70 kg
    }
}
