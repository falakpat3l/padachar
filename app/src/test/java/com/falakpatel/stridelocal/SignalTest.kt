package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.labs.PostureRules
import com.falakpatel.stridelocal.labs.PpgEstimator
import com.falakpatel.stridelocal.labs.RespirationEstimator
import com.falakpatel.stridelocal.sensor.AccelStepDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
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

    @Test fun ppgFinds72Bpm() {
        val e = PpgEstimator()
        val rnd = Random(3)
        for (i in 0 until 30 * 15) {
            val t = i / 30.0
            e.add(t, 120 + 2 * sin(2 * PI * 1.2 * t) + 0.3 * t + rnd.nextDouble() * 0.5)
        }
        val bpm = e.bpm()
        assertTrue("got $bpm", bpm != null && bpm in 68..76)
    }

    @Test fun ppgRejectsNoise() {
        val e = PpgEstimator()
        val rnd = Random(4)
        for (i in 0 until 30 * 15) e.add(i / 30.0, 120 + rnd.nextDouble() * 4)
        assertNull(e.bpm())
    }

    @Test fun respirationFinds15PerMinute() {
        val r = RespirationEstimator()
        for (i in 0 until 10 * 40) {
            val t = i / 10.0
            val tilt = 0.02 * sin(2 * PI * 0.25 * t)
            r.add(0.0, 9.81 * sin(tilt), 9.81 * cos(tilt))
        }
        val bpm = r.breathsPerMinute()
        assertTrue("got $bpm", bpm != null && bpm in 14..16)
    }

    @Test fun pitch() {
        assertEquals(90f, PostureRules.pitchDegrees(9.81f, 0f), 0.5f)
        assertEquals(0f, PostureRules.pitchDegrees(0f, 9.81f), 0.5f)
    }
}
