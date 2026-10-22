package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.health.GoalProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GoalProgressTest {
    @Test fun reachedAtOrAboveGoal() {
        assertEquals(false, GoalProgress.reached(7_999, 8_000))
        assertTrue(GoalProgress.reached(8_000, 8_000))
        assertTrue(GoalProgress.reached(9_240, 8_000))
        assertEquals(false, GoalProgress.reached(500, 0)) // no goal set
    }

    @Test fun percentRoundsDownAndGoesPastHundred() {
        assertEquals(0, GoalProgress.percent(0, 8_000))
        assertEquals(99, GoalProgress.percent(7_999, 8_000))
        assertEquals(100, GoalProgress.percent(8_000, 8_000))
        assertEquals(115, GoalProgress.percent(9_240, 8_000))
        assertEquals(250, GoalProgress.percent(20_000, 8_000))
        assertEquals(0, GoalProgress.percent(5_000, 0))
    }

    @Test fun percentOfDecimals() {
        assertEquals(29, GoalProgress.percentOf(0.29, 1.0))
        assertEquals(100, GoalProgress.percentOf(5.8, 5.8))
        assertEquals(112, GoalProgress.percentOf(6.5, 5.8))
        assertEquals(0, GoalProgress.percentOf(3.0, 0.0))
    }

    @Test fun overText() {
        assertEquals("", GoalProgress.overText(7_000, 8_000, Locale.US))
        assertEquals("Right on your goal", GoalProgress.overText(8_000, 8_000, Locale.US))
        assertEquals("+1,240 over your goal", GoalProgress.overText(9_240, 8_000, Locale.US))
    }

    @Test fun daysReachedText() {
        assertEquals("", GoalProgress.daysReachedText(listOf(0, 3_000, 7_999), 8_000))
        assertEquals("Goal reached on 1 day.", GoalProgress.daysReachedText(listOf(8_000, 3_000), 8_000))
        assertEquals("Goal reached on 3 days.", GoalProgress.daysReachedText(listOf(8_000, 9_000, 12_000, 10), 8_000))
    }
}
