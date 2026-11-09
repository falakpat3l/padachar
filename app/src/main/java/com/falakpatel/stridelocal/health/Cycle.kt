package com.falakpatel.stridelocal.health

import kotlin.math.roundToInt

/**
 * Period and cycle tracking: the data and the maths. Pure Kotlin, unit tested.
 *
 * Everything is estimated from the user's own logged days. Nothing here is a medical
 * prediction, and the app never shows fertile days or "safe days": the screen says the
 * estimates are not for birth control.
 */

/** Bleeding on a day. [code] is what is saved, so the order of this list can change freely. */
enum class Flow(val code: Int, val label: String) {
    NONE(0, "None"),
    SPOTTING(1, "Spotting"),
    LIGHT(2, "Light"),
    MEDIUM(3, "Medium"),
    HEAVY(4, "Heavy"),
    ;

    /** Light, medium or heavy. Spotting alone does not start or extend a period. */
    val isPeriod: Boolean get() = code >= LIGHT.code

    companion object {
        fun ofCode(code: Int): Flow? = entries.firstOrNull { it.code == code }
    }
}

/** How a day felt. [bit] is its place in the saved number, so never reuse or change one. */
enum class Symptom(val bit: Int, val label: String) {
    CRAMPS(0, "Cramps"),
    HEADACHE(1, "Headache"),
    BACK_PAIN(2, "Back pain"),
    BLOATING(3, "Bloating"),
    TENDER(4, "Tender breasts"),
    ACNE(5, "Acne"),
    TIRED(6, "Tired"),
    LOW_MOOD(7, "Low mood"),
    MOOD_SWINGS(8, "Mood swings"),
    CRAVINGS(9, "Cravings"),
    ;

    companion object {
        fun toBits(set: Set<Symptom>): Int = set.fold(0) { acc, s -> acc or (1 shl s.bit) }
        fun fromBits(bits: Int): Set<Symptom> = entries.filter { bits and (1 shl it.bit) != 0 }.toSet()
    }
}

/** One logged day. A day with no flow and no symptoms is not kept. */
data class CycleDay(val epochDay: Long, val flow: Flow = Flow.NONE, val symptoms: Set<Symptom> = emptySet()) {
    val isEmpty: Boolean get() = flow == Flow.NONE && symptoms.isEmpty()
}

/**
 * The log as one short text: "epochDay:flow:symptomBits" for each day, joined by ";".
 * Small enough for years of entries, and easy to read back in a backup.
 */
object CycleLog {
    fun encode(days: Collection<CycleDay>): String =
        days.filterNot { it.isEmpty }.sortedBy { it.epochDay }
            .joinToString(";") { "${it.epochDay}:${it.flow.code}:${Symptom.toBits(it.symptoms)}" }

    /** Damaged parts are skipped. When a day appears twice, the last one wins. */
    fun decode(text: String?): List<CycleDay> {
        if (text.isNullOrBlank()) return emptyList()
        val byDay = LinkedHashMap<Long, CycleDay>()
        text.split(';').forEach { part -> parseDay(part)?.let { byDay[it.epochDay] = it } }
        return byDay.values.filterNot { it.isEmpty }.sortedBy { it.epochDay }
    }

    /** "epochDay:flow:bits", as in [encode] and in backup lines. Null when it cannot be read. */
    fun parseDay(part: String): CycleDay? {
        val f = part.trim().split(':')
        if (f.size != 3) return null
        val day = f[0].toLongOrNull() ?: return null
        val flow = f[1].toIntOrNull()?.let { Flow.ofCode(it) } ?: return null
        val bits = f[2].toIntOrNull() ?: return null
        return CycleDay(day, flow, Symptom.fromBits(bits))
    }

    /** [day] replaces the same date in [days], or removes it when it is empty. */
    fun put(days: List<CycleDay>, day: CycleDay): List<CycleDay> =
        (days.filter { it.epochDay != day.epochDay } + listOfNotNull(day.takeUnless { it.isEmpty })).sortedBy { it.epochDay }

    /** Days from [incoming] whose date is not in [days] yet. The phone's own entries always win. */
    fun missing(days: List<CycleDay>, incoming: List<CycleDay>): List<CycleDay> {
        val have = days.mapTo(HashSet()) { it.epochDay }
        return incoming.filter { !it.isEmpty && it.epochDay !in have }.distinctBy { it.epochDay }
    }
}

/** A period from its first to its last day of bleeding (both included). */
data class Period(val start: Long, val end: Long) {
    val days: Int get() = (end - start + 1).toInt()
}

/** What the Cycle screen shows. Lengths are whole days. */
data class CycleSummary(
    val lastStart: Long,
    /** 1 on the first day of the latest period. */
    val dayOfCycle: Int,
    /** True while the latest period is still going (bleeding logged today or yesterday). */
    val periodNow: Boolean,
    val usualCycle: Int,
    /** False until two periods are logged: [usualCycle] is then the common 28 days. */
    val cycleFromHistory: Boolean,
    val shortestCycle: Int?,
    val longestCycle: Int?,
    val usualPeriod: Int,
    /** First day of the next period, estimated. */
    val nextStart: Long,
    /** Days from today to [nextStart]. Negative when it is late. */
    val daysUntilNext: Long,
)

object CycleMath {
    /** Cycles shorter or longer than this are left out of the averages (most likely a missed log). */
    const val MIN_CYCLE = 15
    const val MAX_CYCLE = 60

    /** Used until there is enough history. */
    const val DEFAULT_CYCLE = 28
    const val DEFAULT_PERIOD = 5

    /** Only the latest cycles count, so the estimate follows changes over time. */
    const val RECENT = 6

    /** Bleeding days at most this many days apart belong to the same period (one missed day is fine). */
    private const val MAX_GAP = 2

    fun periods(days: List<CycleDay>): List<Period> {
        val bleeding = days.filter { it.flow.isPeriod }.map { it.epochDay }.distinct().sorted()
        val out = mutableListOf<Period>()
        for (d in bleeding) {
            val last = out.lastOrNull()
            if (last != null && d - last.end <= MAX_GAP) out[out.size - 1] = last.copy(end = d) else out += Period(d, d)
        }
        return out
    }

    /** Days between the starts of periods that follow each other, oldest first, outliers removed. */
    fun cycleLengths(periods: List<Period>): List<Int> =
        periods.zipWithNext { a, b -> (b.start - a.start).toInt() }.filter { it in MIN_CYCLE..MAX_CYCLE }

    /** Null until the first period is logged. */
    fun summary(days: List<CycleDay>, today: Long): CycleSummary? {
        val periods = periods(days).filter { it.start <= today }
        val latest = periods.lastOrNull() ?: return null
        val periodNow = today - latest.end <= 1
        val cycles = cycleLengths(periods).takeLast(RECENT)
        val usualCycle = if (cycles.isEmpty()) DEFAULT_CYCLE else cycles.average().roundToInt()
        // The period that is still going is left out, because it is not finished yet.
        val finished = (if (periodNow) periods.dropLast(1) else periods).takeLast(RECENT)
        val usualPeriod = if (finished.isEmpty()) DEFAULT_PERIOD else finished.map { it.days }.average().roundToInt()
        val nextStart = latest.start + usualCycle
        return CycleSummary(
            lastStart = latest.start,
            dayOfCycle = (today - latest.start + 1).toInt(),
            periodNow = periodNow,
            usualCycle = usualCycle,
            cycleFromHistory = cycles.isNotEmpty(),
            shortestCycle = cycles.minOrNull(),
            longestCycle = cycles.maxOrNull(),
            usualPeriod = usualPeriod,
            nextStart = nextStart,
            daysUntilNext = nextStart - today,
        )
    }

    /** Days the next period is expected on, for the calendar. Empty while a period is going on. */
    fun expectedDays(s: CycleSummary?): LongRange =
        if (s == null || s.periodNow) LongRange.EMPTY else s.nextStart until s.nextStart + s.usualPeriod

    /** One line for the top of the screen, for example "Next period in about 9 days". */
    fun nextText(s: CycleSummary): String = when {
        s.periodNow -> "Period day ${s.dayOfCycle}"
        s.daysUntilNext > 1 -> "Next period in about ${s.daysUntilNext} days"
        s.daysUntilNext == 1L -> "Next period expected tomorrow"
        s.daysUntilNext == 0L -> "Next period expected today"
        s.daysUntilNext == -1L -> "Period expected 1 day ago"
        else -> "Period expected ${-s.daysUntilNext} days ago"
    }

    /** "Your cycle: about 29 days (27 to 31)", or the default when there is no history yet. */
    fun cycleText(s: CycleSummary): String = when {
        !s.cycleFromHistory -> "Using a usual ${s.usualCycle} day cycle until you log your next period"
        s.shortestCycle != null && s.longestCycle != null && s.shortestCycle != s.longestCycle ->
            "Your cycle: about ${s.usualCycle} days (${s.shortestCycle} to ${s.longestCycle})"
        else -> "Your cycle: about ${s.usualCycle} days"
    }

    /**
     * The day a "period may start tomorrow" reminder is due, or null when none should be planned:
     * no period logged yet, a period going on now, or the expected day already passed.
     */
    fun reminderDay(s: CycleSummary?, today: Long): Long? {
        if (s == null || s.periodNow) return null
        val day = s.nextStart - 1
        return day.takeIf { it >= today }
    }
}
