package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.health.CycleDay
import com.falakpatel.stridelocal.health.CycleLog
import com.falakpatel.stridelocal.health.CycleMath
import com.falakpatel.stridelocal.health.Flow
import com.falakpatel.stridelocal.health.Period
import com.falakpatel.stridelocal.health.Symptom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleTest {
    /** [length] bleeding days starting on [start]. */
    private fun period(start: Long, length: Int, flow: Flow = Flow.MEDIUM) =
        (0 until length).map { CycleDay(start + it, flow) }

    @Test fun logSurvivesEncodeAndDecode() {
        val days = listOf(
            CycleDay(20_000, Flow.HEAVY, setOf(Symptom.CRAMPS, Symptom.TIRED)),
            CycleDay(19_999, Flow.SPOTTING),
            CycleDay(20_003, Flow.NONE, setOf(Symptom.CRAVINGS)),
        )
        val text = CycleLog.encode(days)
        assertEquals("19999:1:0;20000:4:65;20003:0:512", text)
        assertEquals(days.sortedBy { it.epochDay }, CycleLog.decode(text))
    }

    @Test fun emptyDaysAreNotKept() {
        assertEquals("", CycleLog.encode(listOf(CycleDay(5))))
        val start = listOf(CycleDay(10, Flow.LIGHT))
        assertEquals(emptyList<CycleDay>(), CycleLog.put(start, CycleDay(10)))
        assertEquals(listOf(CycleDay(10, Flow.HEAVY)), CycleLog.put(start, CycleDay(10, Flow.HEAVY)))
    }

    @Test fun damagedTextIsSkipped() {
        assertEquals(emptyList<CycleDay>(), CycleLog.decode(null))
        assertEquals(emptyList<CycleDay>(), CycleLog.decode(""))
        assertEquals(listOf(CycleDay(3, Flow.LIGHT)), CycleLog.decode("x;1:9:0;2:1;3:2:0;4:2:y"))
        // The same day twice: the last one wins.
        assertEquals(listOf(CycleDay(3, Flow.HEAVY)), CycleLog.decode("3:2:0;3:4:0"))
    }

    @Test fun restoreOnlyAddsMissingDays() {
        val phone = listOf(CycleDay(10, Flow.LIGHT))
        val backup = listOf(CycleDay(10, Flow.HEAVY), CycleDay(11, Flow.MEDIUM), CycleDay(12))
        assertEquals(listOf(CycleDay(11, Flow.MEDIUM)), CycleLog.missing(phone, backup))
    }

    @Test fun bleedingDaysGroupIntoPeriods() {
        val days = period(100, 4) + CycleDay(106, Flow.LIGHT) + period(130, 5) +
            CycleDay(134 + 2, Flow.LIGHT) + // one day missed: same period
            CycleDay(150, Flow.SPOTTING) // spotting alone is not a period
        assertEquals(listOf(Period(100, 103), Period(106, 106), Period(130, 136)), CycleMath.periods(days))
    }

    @Test fun nothingLoggedMeansNoSummary() {
        assertNull(CycleMath.summary(emptyList(), 1_000))
        assertNull(CycleMath.summary(listOf(CycleDay(990, Flow.SPOTTING)), 1_000))
    }

    @Test fun onePeriodUsesTheUsualCycle() {
        val s = CycleMath.summary(period(1_000, 5), today = 1_010)!!
        assertEquals(11, s.dayOfCycle)
        assertFalse(s.periodNow)
        assertFalse(s.cycleFromHistory)
        assertEquals(CycleMath.DEFAULT_CYCLE, s.usualCycle)
        assertEquals(5, s.usualPeriod)
        assertEquals(1_028L, s.nextStart)
        assertEquals(18L, s.daysUntilNext)
    }

    @Test fun averagesComeFromRecentHistory() {
        // Cycles of 27, 31 and 29 days; periods of 4, 6, 5 and 5 days.
        val days = period(1_000, 4) + period(1_027, 6) + period(1_058, 5) + period(1_087, 5)
        val s = CycleMath.summary(days, today = 1_100)!!
        assertTrue(s.cycleFromHistory)
        assertEquals(29, s.usualCycle)
        assertEquals(27, s.shortestCycle)
        assertEquals(31, s.longestCycle)
        assertEquals(5, s.usualPeriod)
        assertEquals(1_087L + 29, s.nextStart)
        assertEquals("Your cycle: about 29 days (27 to 31)", CycleMath.cycleText(s))
        assertEquals("Next period in about 16 days", CycleMath.nextText(s))
    }

    @Test fun aMissedLogDoesNotSpoilTheAverage() {
        // 1,000 to 1,090 is one cycle that was not logged in between: far too long, left out.
        val days = period(1_000, 5) + period(1_090, 5) + period(1_118, 5)
        val s = CycleMath.summary(days, today = 1_125)!!
        assertEquals(28, s.usualCycle)
        assertEquals(28, s.shortestCycle)
    }

    @Test fun ongoingPeriodIsShownAndNotAveraged() {
        val days = period(1_000, 7) + period(1_030, 2)
        val s = CycleMath.summary(days, today = 1_031)!!
        assertTrue(s.periodNow)
        assertEquals(2, s.dayOfCycle)
        assertEquals(7, s.usualPeriod) // the 2 days so far do not count yet
        assertEquals("Period day 2", CycleMath.nextText(s))
        assertTrue(CycleMath.expectedDays(s).isEmpty())
        assertNull(CycleMath.reminderDay(s, 1_031))
    }

    @Test fun latePeriodIsSaidPlainly() {
        val s = CycleMath.summary(period(1_000, 5), today = 1_031)!!
        assertEquals(-3L, s.daysUntilNext)
        assertEquals("Period expected 3 days ago", CycleMath.nextText(s))
        assertNull(CycleMath.reminderDay(s, 1_031))
    }

    @Test fun expectedDaysAndReminder() {
        val s = CycleMath.summary(period(1_000, 5), today = 1_010)!!
        assertEquals(1_028L until 1_033L, CycleMath.expectedDays(s))
        assertEquals(1_027L, CycleMath.reminderDay(s, 1_010))
        assertEquals(1_027L, CycleMath.reminderDay(s, 1_027))
        assertNull(CycleMath.reminderDay(s, 1_028))
        assertNull(CycleMath.reminderDay(null, 1_010))
    }

    @Test fun shortNextTexts() {
        val base = CycleMath.summary(period(1_000, 5), today = 1_010)!!
        assertEquals("Next period expected tomorrow", CycleMath.nextText(base.copy(daysUntilNext = 1)))
        assertEquals("Next period expected today", CycleMath.nextText(base.copy(daysUntilNext = 0)))
        assertEquals("Period expected 1 day ago", CycleMath.nextText(base.copy(daysUntilNext = -1)))
    }
}
