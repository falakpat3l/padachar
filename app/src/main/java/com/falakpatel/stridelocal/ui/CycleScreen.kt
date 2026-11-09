package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.health.CycleDay
import com.falakpatel.stridelocal.health.CycleMath
import com.falakpatel.stridelocal.health.CycleSummary
import com.falakpatel.stridelocal.health.Flow
import com.falakpatel.stridelocal.health.Symptom
import com.falakpatel.stridelocal.reminder.CycleReminder
import com.falakpatel.stridelocal.sensor.DayClock
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private fun date(epochDay: Long, pattern: String): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

/** How strongly a logged day is filled in on the calendar. */
private fun Flow.fill(): Float = when (this) {
    Flow.HEAVY -> 1f
    Flow.MEDIUM -> 0.75f
    Flow.LIGHT -> 0.5f
    else -> 0f
}

/**
 * Period and cycle log: a summary with the next estimated period, a month calendar to log
 * days, and the latest periods. Shown only when Cycle tracking is on in Settings.
 * Everything stays on the phone, like the rest of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen() {
    val app = LocalContext.current.strideApp
    val prefs = app.userPreferences
    val days by remember { prefs.cycleDays }.collectAsStateWithLifecycle(emptyList())
    val today = DayClock.today()
    val summary = remember(days, today) { CycleMath.summary(days, today) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<Long?>(null) }

    fun save(changed: List<CycleDay>) {
        app.appScope.launch {
            changed.forEach { prefs.saveCycleDay(it) }
            runCatching { CycleReminder.reschedule(app) }
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Cycle") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard(summary, onLogToday = { editing = today })
            CalendarCard(month, { month = it }, days, summary, today, onPick = { editing = it })
            PeriodList(days)
            Text(
                "Estimates come only from the days you log. They are not for birth control and are not medical advice. " +
                    "Your log stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    editing?.let { day ->
        DayDialog(
            day = day,
            today = today,
            days = days,
            usualPeriod = summary?.usualPeriod ?: CycleMath.DEFAULT_PERIOD,
            onDismiss = { editing = null },
            onSave = { save(it); editing = null },
        )
    }
}

@Composable
private fun SummaryCard(s: CycleSummary?, onLogToday: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (s == null) {
                Text("No periods logged yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Tap a day on the calendar to log your last period. The next one is estimated from there.",
                    style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    CycleMath.nextText(s),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                if (!s.periodNow) {
                    Text("Around ${date(s.nextStart, "EEEE d MMMM")}", style = MaterialTheme.typography.bodyMedium)
                    Text("Day ${s.dayOfCycle} of your cycle", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Text(CycleMath.cycleText(s), style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center)
                Text("Usual period: about ${s.usualPeriod} days", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Spacer(Modifier.size(4.dp))
            Button(onClick = onLogToday) { Text("Log today") }
        }
    }
}

/** One month, weeks in rows. Logged period days are filled in, expected ones outlined. */
@Composable
private fun CalendarCard(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    days: List<CycleDay>,
    s: CycleSummary?,
    today: Long,
    onPick: (Long) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val loc = Locale.getDefault()
    val byDay = remember(days) { days.associateBy { it.epochDay } }
    val expected = CycleMath.expectedDays(s)
    val firstDow = WeekFields.of(loc).firstDayOfWeek
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val cells = offset + month.lengthOfMonth()
    val thisMonth = YearMonth.now()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier month")
                }
                Text(
                    first.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(170.dp),
                )
                // Up to two months ahead, to see the next estimate.
                val canGoOn = month.isBefore(thisMonth.plusMonths(2))
                IconButton(onClick = { if (canGoOn) onMonth(month.plusMonths(1)) }, enabled = canGoOn) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later month")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                (0L until 7L).forEach { i ->
                    Text(
                        firstDow.plus(i).getDisplayName(TextStyle.NARROW, loc),
                        style = MaterialTheme.typography.labelSmall, color = muted,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            (0 until (cells + 6) / 7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { col ->
                        val n = week * 7 + col - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (n in 1..month.lengthOfMonth()) {
                                val d = month.atDay(n).toEpochDay()
                                DayCell(n, byDay[d], d in expected, d == today, d <= today, accent, muted) { onPick(d) }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Modifier.background(accent, CircleShape), "Period")
                Legend(Modifier.border(1.5.dp, accent.copy(alpha = 0.7f), CircleShape), "Expected")
                Legend(Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape), "Today")
            }
        }
    }
}

@Composable
private fun DayCell(
    number: Int,
    logged: CycleDay?,
    expected: Boolean,
    isToday: Boolean,
    canLog: Boolean,
    accent: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    val fill = logged?.flow?.fill() ?: 0f
    val textColor = when {
        fill >= 0.75f -> Color.Black
        !canLog -> muted
        else -> MaterialTheme.colorScheme.onSurface
    }
    val label = buildString {
        append(number)
        if (logged != null && logged.flow != Flow.NONE) append(", ").append(logged.flow.label.lowercase()).append(" flow")
        if (logged != null && logged.symptoms.isNotEmpty()) append(", ").append(logged.symptoms.joinToString { it.label.lowercase() })
        if (expected) append(", period expected")
        if (isToday) append(", today")
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (fill > 0f) Modifier.background(accent.copy(alpha = fill), CircleShape) else Modifier)
            .then(
                when {
                    isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    expected && fill == 0f -> Modifier.border(1.5.dp, accent.copy(alpha = 0.7f), CircleShape)
                    else -> Modifier
                },
            )
            .clickable(enabled = canLog, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        // A small dot under the number: spotting or symptoms logged on a day without a period.
        val dot = logged != null && fill == 0f && (logged.flow == Flow.SPOTTING || logged.symptoms.isNotEmpty())
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .background(if (logged?.flow == Flow.SPOTTING) accent else muted, CircleShape),
            )
        }
    }
}

@Composable
private fun Legend(swatch: Modifier, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).then(swatch))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The latest periods, newest first, with how long each cycle lasted. */
@Composable
private fun PeriodList(days: List<CycleDay>) {
    val periods = remember(days) { CycleMath.periods(days) }
    if (periods.isEmpty()) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent periods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            periods.withIndex().reversed().take(CycleMath.RECENT).forEach { (i, p) ->
                val next = periods.getOrNull(i + 1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(date(p.start, "d MMM yyyy"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (p.days == 1) "1 day" else "${p.days} days",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (next != null) {
                    Text(
                        "Cycle: ${next.start - p.start} days",
                        style = MaterialTheme.typography.bodySmall, color = muted,
                    )
                }
            }
        }
    }
}

/**
 * Log one day: flow and how it felt. For a past day that starts a new period, the days after
 * it (up to today) can be filled in too, so an earlier period is logged in one go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDialog(
    day: Long,
    today: Long,
    days: List<CycleDay>,
    usualPeriod: Int,
    onDismiss: () -> Unit,
    onSave: (List<CycleDay>) -> Unit,
) {
    val existing = remember(day, days) { days.firstOrNull { it.epochDay == day } }
    var flow by remember(day) { mutableStateOf(existing?.flow ?: Flow.NONE) }
    var symptoms by remember(day) { mutableStateOf(existing?.symptoms ?: emptySet()) }
    // Bleeding logged on one of the two days before means this day continues that period.
    val continues = days.any { it.flow.isPeriod && it.epochDay in (day - 2) until day }
    val fillCount = if (flow.isPeriod && !continues) minOf((usualPeriod - 1).toLong(), today - day).coerceAtLeast(0L) else 0L
    var fillNext by remember(day) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date(day, "EEEE d MMMM")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Flow", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Flow.entries.forEach { f ->
                        FilterChip(selected = flow == f, onClick = { flow = f }, label = { Text(f.label) })
                    }
                }
                if (fillCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fillNext, onCheckedChange = { fillNext = it })
                        Text(
                            if (fillCount == 1L) "Also mark the next day" else "Also mark the next $fillCount days",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text("How you feel", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symptom.entries.forEach { s ->
                        val on = s in symptoms
                        FilterChip(selected = on, onClick = { symptoms = if (on) symptoms - s else symptoms + s }, label = { Text(s.label) })
                    }
                }
                if (existing != null) {
                    TextButton(onClick = { onSave(listOf(CycleDay(day))) }) { Text("Clear this day") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val main = CycleDay(day, flow, symptoms)
                // Filled days keep anything already logged on them.
                val extra = if (fillNext && fillCount > 0) {
                    (1..fillCount).map { day + it }
                        .filter { d -> days.none { it.epochDay == d } }
                        .map { CycleDay(it, flow) }
                } else {
                    emptyList()
                }
                onSave(listOf(main) + extra)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
