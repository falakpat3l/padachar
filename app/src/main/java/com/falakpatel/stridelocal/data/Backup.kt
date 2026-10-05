package com.falakpatel.stridelocal.data

import android.content.Context
import android.net.Uri
import com.falakpatel.stridelocal.health.Sex
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Backup to a plain CSV file the user picks (Downloads, a USB stick, anywhere). No internet,
 * no permission needed: Android's file picker grants access to that one file only.
 *
 * Lines: P = profile, D = one day of steps, F = one food item.
 * Restore never deletes anything: days keep the higher step count, food is skipped if the
 * same item at the same time is already there, so restoring twice is safe.
 */
object Backup {
    private const val HEADER = "# StrideLocal backup v1"

    suspend fun export(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val app = ctx.strideApp
        val p = app.userPreferences.profile.first()
        val days = app.stepRepository.allDays()
        val food = app.stepRepository.allFood()
        val out = ctx.contentResolver.openOutputStream(uri) ?: error("Could not open the file")
        out.bufferedWriter().use { w ->
            w.write(HEADER + "\n")
            w.write("P,${p.weightKg},${p.heightCm},${p.ageYears},${p.sex.name},${p.dailyGoal},${p.strideOverrideM ?: ""},${p.accentArgb}\n")
            days.forEach { w.write("D,${it.epochDay},${it.steps},${it.distanceKm},${it.activeKcal},${it.goal}\n") }
            food.forEach {
                w.write("F,${it.epochDay},${it.timeMs},${Csv.field(it.name)},${it.servings},${it.kcal},${it.proteinG},${it.carbsG},${it.fatG}\n")
            }
        }
        days.size + food.size
    }

    /** @return number of records added or updated. */
    suspend fun restore(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val app = ctx.strideApp
        val repo = app.stepRepository
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Could not open the file")
        val lines = input.bufferedReader().use { it.readLines() }
        require(lines.firstOrNull()?.startsWith("# StrideLocal backup") == true) { "This is not a StrideLocal backup file" }
        var changed = 0
        for (line in lines.drop(1)) {
            val f = Csv.split(line)
            runCatching {
                when (f[0]) {
                    "P" -> {
                        app.userPreferences.save(
                            UserProfile(f[1].toDouble(), f[2].toDouble(), f[3].toInt(), Sex.valueOf(f[4]), f[5].toInt(), f[6].toDoubleOrNull(), true),
                        )
                        app.userPreferences.saveAccent(f[7].toInt())
                    }
                    "D" -> {
                        val row = DailySteps(f[1].toLong(), f[2].toLong(), f[3].toDouble(), f[4].toDouble(), f[5].toInt())
                        val old = repo.getDay(row.epochDay)
                        if (old == null || row.steps > old.steps) {
                            repo.upsertDays(listOf(row))
                            changed++
                        }
                    }
                    "F" -> {
                        val e = FoodEntry(0, f[1].toLong(), f[2].toLong(), f[3], f[4].toDouble(), f[5].toDouble(), f[6].toDouble(), f[7].toDouble(), f[8].toDouble())
                        if (!repo.hasFood(e.timeMs, e.name)) {
                            repo.addFood(e)
                            changed++
                        }
                    }
                }
            } // a damaged line is skipped instead of stopping the whole restore
        }
        changed
    }
}
