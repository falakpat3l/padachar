package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.data.Csv
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.IndianDishes
import com.falakpatel.stridelocal.data.mergeRestoredDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodAndBackupTest {
    @Test fun csvRoundTripWithCommasAndQuotes() {
        val name = "Dal, \"homemade\""
        val line = "F,1,2,${Csv.field(name)},1.5"
        assertEquals(listOf("F", "1", "2", name, "1.5"), Csv.split(line))
    }

    @Test fun csvPlainFieldsUntouched() = assertEquals("Poha", Csv.field("Poha"))

    @Test fun lineBreakInNameStaysOnOneLine() {
        val field = Csv.field("Dal\nrice,\r\nhome")
        assertEquals(false, field.contains('\n') || field.contains('\r'))
        assertEquals(listOf("F", "Dal rice, home", "1.5"), Csv.split("F,$field,1.5"))
    }

    @Test fun oldBackupWithSplitNameIsJoined() {
        // Written by older versions: the quoted name kept its line break.
        val lines = listOf("D,1,500,0.4,20.0,8000", "F,1,2,\"Dal,", "rice\",1.0,300.0,9.0,40.0,8.0", "F,1,3,Poha,1.0,250.0,5.0,45.0,6.0")
        val records = Csv.records(lines)
        assertEquals(3, records.size)
        assertEquals("Dal, rice", Csv.split(records[1])[3])
        assertEquals("Poha", Csv.split(records[2])[3])
    }

    @Test fun damagedLineDoesNotSwallowTheNext() {
        val lines = listOf("F,1,2,\"broken,1.0", "D,2,900,0.7,30.0,8000", "D,3,100,0.1,5.0,8000")
        assertEquals(lines, Csv.records(lines))
    }

    @Test fun restoreKeepsTodaysLiveCount() {
        val phone = DailySteps(100, steps = 4_000, distanceKm = 3.0, activeKcal = 150.0, goal = 9_000, walkMin = 30)
        val backup = DailySteps(100, steps = 2_500, distanceKm = 2.0, activeKcal = 90.0, goal = 8_000, walkMin = 35, runMin = 2, runSteps = 300)
        val merged = mergeRestoredDay(phone, backup)!!
        assertEquals(4_000L, merged.steps)
        assertEquals(3.0, merged.distanceKm, 0.0)
        assertEquals(9_000, merged.goal)
        assertEquals(35, merged.walkMin)
        assertEquals(300L, merged.runSteps)
        assertEquals(null, mergeRestoredDay(merged, backup)) // restoring twice changes nothing
        assertEquals(backup, mergeRestoredDay(null, backup))
    }

    @Test fun dishNamesUniqueAndSane() {
        val all = IndianDishes.all
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertTrue(all.all { it.kcal > 0 && it.protein >= 0 })
        // macros roughly agree with kcal (4/4/9 rule), within 35 percent
        assertTrue(all.all { d -> val est = 4 * d.protein + 4 * d.carbs + 9 * d.fat; kotlin.math.abs(est - d.kcal) / d.kcal < 0.35 })
    }

    @Test fun searchMatchesWordStarts() {
        val names = IndianDishes.search("dal").map { it.name }
        assertTrue("Toor dal" in names && "Gujarati dal" in names)
        assertTrue(IndianDishes.search("ROTI").any { it.name.startsWith("Roti") })
        assertEquals(IndianDishes.all.size, IndianDishes.search(" ").size)
    }
}
