package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.reminder.ReminderSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class ReminderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, day: Int = 6) = LocalDate.of(2026, 10, day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun daytimeGapIs75To105Minutes() {
        repeat(50) { seed ->
            val now = at(10, 0)
            val gapMin = (ReminderSchedule.next(now, Random(seed), zone) - now) / 60_000
            assertTrue("gap $gapMin", gapMin in 75..105)
        }
    }

    @Test fun eveningRollsToNextMorning() {
        repeat(50) { seed ->
            val next = ReminderSchedule.next(at(20, 30), Random(seed), zone)
            assertTrue(next in at(7, 0, 7)..at(8, 0, 7))
        }
    }

    @Test fun earlyMorningWaitsUntilSeven() {
        val next = ReminderSchedule.next(at(4, 0), Random(1), zone)
        assertTrue(next in at(7, 0)..at(8, 0))
    }

    @Test fun windowIsSevenToNine() {
        assertTrue(ReminderSchedule.inWindow(at(7, 0), zone))
        assertTrue(ReminderSchedule.inWindow(at(20, 59), zone))
        assertEquals(false, ReminderSchedule.inWindow(at(21, 0), zone))
        assertEquals(false, ReminderSchedule.inWindow(at(6, 59), zone))
    }

    @Test fun skipsWhenAlreadyWalking() {
        assertEquals(false, ReminderSchedule.shouldRemind(1_500, 1_000, sameDay = true))
        assertTrue(ReminderSchedule.shouldRemind(1_100, 1_000, sameDay = true))
        assertTrue(ReminderSchedule.shouldRemind(5_000, 9_000, sameDay = false))
    }
}
