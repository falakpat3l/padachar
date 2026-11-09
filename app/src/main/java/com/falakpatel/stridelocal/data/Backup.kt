package com.falakpatel.stridelocal.data

import android.content.Context
import android.net.Uri
import com.falakpatel.stridelocal.health.CycleDay
import com.falakpatel.stridelocal.health.CycleLog
import com.falakpatel.stridelocal.health.Sex
import com.falakpatel.stridelocal.health.Symptom
import com.falakpatel.stridelocal.sensor.StepEngine
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

/**
 * Backup to a plain CSV file the user picks (Downloads, a USB stick, anywhere). No internet,
 * no permission needed: Android's file picker grants access to that one file only.
 *
 * Lines: P = profile, D = one day of steps, F = one food item, C = one day of the cycle log
 * (left out when "Include the cycle log in backups" is off).
 * Restore never deletes anything: each day keeps the higher of each total (so steps counted
 * today are never lowered), food is skipped if the same item at the same time is already
 * there, and a cycle day is only added when that date is not logged on the phone yet, so
 * restoring twice is safe.
 */
object Backup {
    private const val HEADER = "# Padachar backup v1"

    // The picked file could be anything, so restore stops early on a file far bigger than any
    // real backup (years of steps and food fit in a few MB), instead of running out of memory.
    private const val MAX_BYTES = 16L * 1024 * 1024
    private const val MAX_LINES = 250_000

    suspend fun export(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val app = ctx.strideApp
        val p = app.userPreferences.profile.first()
        val days = app.stepRepository.allDays()
        val food = app.stepRepository.allFood()
        val cycle = if (app.userPreferences.cycleInBackup.first()) app.userPreferences.cycleDays.first() else emptyList()
        // "wt" empties the file first, so an older, longer backup leaves no old lines at the end.
        // A few storage apps do not support it; plain "w" is the fallback.
        val out = runCatching { ctx.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: ctx.contentResolver.openOutputStream(uri) ?: error("Could not open the file")
        out.bufferedWriter().use { w ->
            w.write(HEADER + "\n")
            w.write("P,${p.weightKg},${p.heightCm},${p.ageYears},${p.sex.name},${p.dailyGoal},${p.strideOverrideM ?: ""},${p.accentArgb},${p.foodGoalKcal}\n")
            days.forEach { w.write("D,${it.epochDay},${it.steps},${it.distanceKm},${it.activeKcal},${it.goal},${it.walkMin},${it.runMin},${it.runSteps}\n") }
            food.forEach {
                w.write("F,${it.epochDay},${it.timeMs},${Csv.field(it.name)},${it.servings},${it.kcal},${it.proteinG},${it.carbsG},${it.fatG}\n")
            }
            cycle.forEach { w.write("C,${it.epochDay},${it.flow.code},${Symptom.toBits(it.symptoms)}\n") }
        }
        days.size + food.size + cycle.size
    }

    /** @return number of records added or updated. */
    suspend fun restore(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val app = ctx.strideApp
        val repo = app.stepRepository
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Could not open the file")
        val lines = LimitedInput(input, MAX_BYTES).bufferedReader().useLines { it.take(MAX_LINES + 1).toList() }
        require(lines.size <= MAX_LINES) { "This file is too big to be a Padachar backup" }
        // Backups made before the rename say "StrideLocal"; both are accepted.
        // Some editors add an invisible byte order mark at the start when they save the file.
        val first = lines.firstOrNull().orEmpty().removePrefix("\uFEFF")
        require(first.startsWith("# Padachar backup") || first.startsWith("# StrideLocal backup")) { "This is not a Padachar backup file" }
        var changed = 0
        val cycle = mutableListOf<CycleDay>()
        for (line in Csv.records(lines.drop(1))) {
            val f = Csv.split(line)
            runCatching {
                when (f[0]) {
                    "P" -> {
                        app.userPreferences.save(
                            UserProfile(
                                f[1].toDouble(), f[2].toDouble(), f[3].toInt(), Sex.valueOf(f[4]), f[5].toInt(), f[6].toDoubleOrNull(), true,
                                foodGoalKcal = f.getOrNull(8)?.toIntOrNull() ?: 0, // older backups have no food goal
                            ),
                        )
                        app.userPreferences.saveAccent(f[7].toInt())
                    }
                    "D" -> {
                        val row = DailySteps(
                            f[1].toLong(), f[2].toLong(), f[3].toDouble(), f[4].toDouble(), f[5].toInt(),
                            walkMin = f.getOrNull(6)?.toIntOrNull() ?: 0, // older backups have no walk/run columns
                            runMin = f.getOrNull(7)?.toIntOrNull() ?: 0,
                            runSteps = f.getOrNull(8)?.toLongOrNull() ?: 0,
                        )
                        // Under the step lock, so steps counted live during the restore are kept.
                        val raised = StepEngine.withWriteLock {
                            val merged = mergeRestoredDay(repo.getDay(row.epochDay), row)
                            if (merged != null) repo.upsertDays(listOf(merged))
                            merged != null
                        }
                        if (raised) changed++
                    }
                    "C" -> CycleLog.parseDay("${f[1]}:${f[2]}:${f[3]}")?.let { cycle += it }
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
        if (cycle.isNotEmpty()) {
            val added = app.userPreferences.addMissingCycleDays(cycle)
            // The backup came from a phone with cycle tracking on, so show the Cycle tab again.
            if (added > 0) app.userPreferences.setCycleEnabled(true)
            changed += added
        }
        changed
    }
}

/** Reads at most [max] bytes, then throws, so a huge file cannot fill the memory. */
private class LimitedInput(private val inner: InputStream, private val max: Long) : InputStream() {
    private var total = 0L

    override fun read(): Int {
        val b = inner.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = inner.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    override fun close() {
        inner.close()
    }

    private fun add(n: Long) {
        total += n
        if (total > max) throw IOException("This file is too big to be a Padachar backup")
    }
}

/**
 * A day from a backup merged into the same day on this phone: each total keeps the higher value
 * and the phone's goal is kept. Returns null when nothing would change. Pure Kotlin, unit tested.
 */
internal fun mergeRestoredDay(old: DailySteps?, restored: DailySteps): DailySteps? {
    if (old == null) return restored
    val merged = old.copy(
        steps = max(old.steps, restored.steps),
        distanceKm = max(old.distanceKm, restored.distanceKm),
        activeKcal = max(old.activeKcal, restored.activeKcal),
        walkMin = max(old.walkMin, restored.walkMin),
        runMin = max(old.runMin, restored.runMin),
        runSteps = max(old.runSteps, restored.runSteps),
    )
    return merged.takeIf { it != old }
}
