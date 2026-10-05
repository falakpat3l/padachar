package com.falakpatel.stridelocal

import com.falakpatel.stridelocal.data.Csv
import com.falakpatel.stridelocal.data.IndianDishes
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
