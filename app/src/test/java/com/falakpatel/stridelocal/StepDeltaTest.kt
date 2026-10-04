package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.sensor.CadenceEstimator
import com.falakpatel.stridelocal.sensor.DayClock
import com.falakpatel.stridelocal.sensor.StepDelta
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class StepDeltaTest {
    @Test fun firstReadingIsBaselineOnly() = assertEquals(0L, StepDelta.compute(null, -1, 52_000, 7))
    @Test fun normalDelta() = assertEquals(120L, StepDelta.compute(1_000, 7, 1_120, 7))
    @Test fun noChange() = assertEquals(0L, StepDelta.compute(1_000, 7, 1_000, 7))
    @Test fun rebootCounterSmaller() = assertEquals(300L, StepDelta.compute(9_000, 7, 300, 8))
    @Test fun rebootDetectedByBootCountEvenWhenCounterIsLarger() =
        assertEquals(12_000L, StepDelta.compute(9_000, 7, 12_000, 8))
    @Test fun resetWithoutBootCount() = assertEquals(50L, StepDelta.compute(9_000, -1, 50, -1))
    @Test fun implausibleJumpIgnored() = assertEquals(0L, StepDelta.compute(0, 7, 5_000_000, 7))

    @Test fun cadenceFromSteadyWalking() {
        val c = CadenceEstimator()
        var t = 0L
        repeat(30) { t += 500; c.add(1, t) } // 1 step per 500 ms = 120 spm
        assertEquals(120.0, c.stepsPerMinute, 1.0)
    }

    @Test fun cadenceFallsBackAfterLongGap() {
        val c = CadenceEstimator(defaultSpm = 100.0)
        c.add(1, 0); c.add(500, 600_000)
        assertEquals(100.0, c.stepsPerMinute, 0.01)
    }

    @Test fun epochDayUsesLocalZone() {
        val zone = ZoneId.of("Asia/Kolkata")
        // 2026-10-03T18:31:00Z is 00:01 on 4 Oct in India
        val ms = java.time.Instant.parse("2026-10-03T18:31:00Z").toEpochMilli()
        assertEquals(java.time.LocalDate.of(2026, 10, 4).toEpochDay(), DayClock.epochDay(ms, zone))
    }
}
