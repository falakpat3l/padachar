package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.sensor.MinutePace
import com.falakpatel.stridelocal.sensor.Pace
import com.falakpatel.stridelocal.sensor.PaceTracker
import com.falakpatel.stridelocal.sensor.paceFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walk or run, judged by how fast the steps come. */
class PaceTest {
    /** Steps every [gapMs] from [fromMs] for [seconds]; returns every finished minute. */
    private fun minutes(vararg bouts: Triple<Long, Int, Long>): List<MinutePace> {
        val t = PaceTracker()
        for ((fromMs, seconds, gapMs) in bouts) {
            var at = fromMs
            while (at < fromMs + seconds * 1000L) { t.onStep(at); at += gapMs }
        }
        return t.drainPaced(0, all = true)
    }
    private fun paces(list: List<MinutePace>) = list.map { paceFor(it.steps, it.runSteps) }

    @Test fun aOneMinuteJogAcrossTwoClockMinutesIsRunning() {
        // 160 steps a minute for 60 s, starting 30 s into a minute: 80 steps in each clock minute.
        // Counting steps per clock minute called both of these walking.
        val m = minutes(Triple(90_000L, 60, 375L))
        assertEquals(listOf(80, 80), m.map { it.steps })
        assertEquals(listOf(Pace.RUN, Pace.RUN), paces(m))
        assertTrue(m.sumOf { it.runSteps } in 150..160)
    }

    @Test fun aFullRunningMinuteIsRunning() {
        val m = minutes(Triple(60_000L, 60, 375L))
        assertEquals(listOf(Pace.RUN), paces(m))
    }

    @Test fun walkingAndBriskWalkingAreWalking() {
        assertEquals(listOf(Pace.WALK), paces(minutes(Triple(60_000L, 60, 600L))))  // 100 a minute
        assertEquals(listOf(Pace.WALK), paces(minutes(Triple(60_000L, 60, 445L))))  // 135 a minute
    }

    @Test fun aFewQuickStepsInAWalkingMinuteStayWalking() {
        // 50 s of walking, then 8 s hurrying across a road.
        val m = minutes(Triple(60_000L, 50, 600L), Triple(110_500L, 8, 375L))
        assertEquals(listOf(Pace.WALK), paces(m))
    }

    @Test fun aPauseDoesNotLookLikeFastSteps() {
        // Slow steps with long gaps never count as running pace.
        val m = minutes(Triple(60_000L, 55, 1_600L))
        assertEquals(0, m.sumOf { it.runSteps })
    }

    @Test fun jitteryRunningStepsStillCount() {
        // The sensor's step times wobble: 300, 450, 300, 450 ms... averages 160 a minute.
        val t = PaceTracker()
        var at = 60_000L
        repeat(150) { t.onStep(at); at += if (it % 2 == 0) 300L else 450L }
        val m = t.drainPaced(0, all = true)
        assertEquals(Pace.RUN, paceFor(m[0].steps, m[0].runSteps))
    }

    @Test fun theOldCountRuleStillHolds() {
        assertEquals(Pace.RUN, paceFor(160, 0))
        assertEquals(Pace.WALK, paceFor(140, 0))
        assertEquals(Pace.IDLE, paceFor(10, 0))
    }
}
