package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import com.falakpatel.stridelocal.R
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.strideApp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.health.DayRings
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.sensor.StepCounterService
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: MainUiState, onEditProfile: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("padachar") },
                actions = {
                    IconButton(onClick = onEditProfile) { Icon(Icons.Filled.Person, contentDescription = "Your metrics") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionGate()
            TodayCard(state.today, state.profile, state.eatenKcal)
            HistoryCard(state.profile.dailyGoal)
            BodyCard(state.profile)
        }
    }
}

/**
 * Asks for ACTIVITY_RECOGNITION (Android 10+), then starts the background service.
 * Notification permission is never requested, so nothing sits in the status bar. If the user says no, explains why and links to settings.
 */
@Composable
private fun PermissionGate() {
    val context = LocalContext.current
    val needed = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }.toTypedArray()
    }
    var activityGranted by remember { mutableStateOf(StepCounterService.hasActivityPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        activityGranted = StepCounterService.hasActivityPermission(context)
        if (activityGranted) StepCounterService.start(context)
    }
    LaunchedEffect(Unit) {
        if (activityGranted) StepCounterService.start(context) else if (needed.isNotEmpty()) launcher.launch(needed)
    }
    if (activityGranted) return

    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Allow physical activity", fontWeight = FontWeight.Bold)
            Text("Needed to count steps. Data stays on this phone.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(needed) }) { Text("Allow") }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Open settings") }
            }
        }
    }
}

/**
 * Today's goals as four straight progress lines: steps, distance, kcal burned, kcal eaten.
 * Each line has a small symbol at the start (where you begin) and a bigger one at the end
 * (the goal). The lines slide to their length when the screen opens.
 */
@Composable
private fun TodayCard(today: DailySteps, profile: UserProfile, eatenKcal: Double) {
    val g = DayRings.of(today, profile, eatenKcal)
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = c1.companion()
    val c3 = c2.companion()
    val c4 = c3.companion()
    val loc = Locale.getDefault()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(String.format(loc, "%,d", today.steps), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = c1)
        Text("steps today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GoalLine(R.drawable.ic_stat_steps, c1, g.steps, String.format(loc, "%,d of %,d steps", today.steps, profile.dailyGoal))
            GoalLine(R.drawable.ic_distance, c2, g.distance, String.format(loc, "%.2f of %.2f km", today.distanceKm, g.distanceGoalKm))
            GoalLine(R.drawable.ic_burn, c3, g.burned, String.format(loc, "%.0f of %.0f kcal burned", today.activeKcal, g.burnGoalKcal))
            GoalLine(R.drawable.ic_eat, c4, g.eaten, String.format(loc, "%.0f of %.0f kcal eaten", eatenKcal, g.eatTargetKcal))
        }
    }
}

/** One goal: small symbol, the progress line, bigger symbol, and the numbers underneath. */
@Composable
private fun GoalLine(@DrawableRes symbol: Int, color: Color, fraction: Float, label: String) {
    val shown by animateFloatAsState(fraction, tween(durationMillis = 800, easing = FastOutSlowInEasing), label = "goal")
    val white = MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(symbol), contentDescription = null, tint = white.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                if (shown > 0f) Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(color, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            Icon(painterResource(symbol), contentDescription = label, tint = white, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stat(value: String, label: String, @DrawableRes symbol: Int? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // White symbols (res/drawable/ic_burn.xml, ic_eat.xml) can be edited or swapped freely.
        if (symbol != null) Icon(painterResource(symbol), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Chart ranges, like Google Fit's W / M / Y tabs. (No "D": the app does not keep hourly data.) */
private enum class Range(val tab: String) { WEEK("W"), MONTH("M"), YEAR("Y") }

/** First and last day shown for a range, [page] steps back from today (0 = the current one). */
private fun rangeDates(range: Range, page: Int, today: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
    Range.WEEK -> today.minusDays(7L * page).let { end -> end.minusDays(6) to end }
    Range.MONTH -> YearMonth.from(today).minusMonths(page.toLong()).let { m ->
        m.atDay(1) to earlier(m.atEndOfMonth(), today)
    }
    Range.YEAR -> YearMonth.from(today).minusMonths(12L * page).let { last ->
        last.minusMonths(11).atDay(1) to earlier(last.atEndOfMonth(), today)
    }
}

private fun earlier(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b

/** Average steps and km per day for each of 12 months (only days with steps count). */
private fun monthlyAverages(days: List<DailySteps>, firstMonth: YearMonth): List<DailySteps> =
    (0L until 12L).map { i ->
        val month = firstMonth.plusMonths(i)
        val active = days.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month && it.steps > 0 }
        DailySteps(
            epochDay = month.atDay(1).toEpochDay(),
            steps = if (active.isEmpty()) 0 else active.sumOf { it.steps } / active.size,
            distanceKm = if (active.isEmpty()) 0.0 else active.sumOf { it.distanceKm } / active.size,
        )
    }

/** Average of the days (or months) that have steps; null when there are none. */
private fun averageSteps(bars: List<DailySteps>): Long? =
    bars.filter { it.steps > 0 }.takeIf { it.isNotEmpty() }?.let { active -> active.sumOf { it.steps } / active.size }

/**
 * Steps history with W / M / Y tabs and arrows to page back in time.
 * Week and month show one bar per day; year shows one bar per month (average steps a day).
 * Under the chart: this month and the two before it, as average steps a day.
 */
@Composable
private fun HistoryCard(goal: Int) {
    val repo = LocalContext.current.strideApp.stepRepository
    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val (from, to) = rangeDates(range, page, today)
    val days by remember(from, to) { repo.observeRange(from.toEpochDay(), to.toEpochDay()) }
        .collectAsStateWithLifecycle(emptyList())
    val bars = if (range == Range.YEAR) monthlyAverages(days, YearMonth.from(from)) else days
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bar = MaterialTheme.colorScheme.primary
    val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // W / M / Y tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    val on = r == range
                    Box(
                        Modifier
                            .size(width = 52.dp, height = 32.dp)
                            .background(if (on) bar else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clickable { range = r; page = 0 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(r.tab, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            // Which dates, with arrows to move back and forward
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier") }
                Text(
                    when (range) {
                        Range.WEEK -> "${from.format(dayFmt)} to ${to.format(dayFmt)}"
                        Range.MONTH -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                        Range.YEAR -> "${from.format(monthFmt)} to ${to.format(monthFmt)}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(190.dp),
                )
                IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later")
                }
            }
            if (bars.isNotEmpty()) {
                StepBarChart(
                    days = bars,
                    parts = { listOf(BarPart(it.steps, bar)) },
                    header = { d ->
                        val date = LocalDate.ofEpochDay(d.epochDay)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(String.format(Locale.getDefault(), "%,d", d.steps), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(if (range == Range.YEAR) "steps a day" else "steps", style = MaterialTheme.typography.titleSmall, color = muted)
                        }
                        Text(
                            if (range == Range.YEAR) date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) + "  ·  average"
                            else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) + String.format(Locale.getDefault(), "  ·  %.2f km", d.distanceKm),
                            style = MaterialTheme.typography.bodySmall, color = muted,
                        )
                    },
                    caption = averageSteps(bars)?.let {
                        String.format(Locale.getDefault(), "Average %,d steps a day. Tap or slide across the bars.", it)
                    } ?: "No steps in this range yet.",
                    goal = goal,
                    average = averageSteps(bars),
                    labelFor = { i, d ->
                        val date = LocalDate.ofEpochDay(d.epochDay)
                        when (range) {
                            Range.WEEK -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            Range.MONTH -> if (date.dayOfMonth == 1 || date.dayOfMonth % 5 == 0) date.dayOfMonth.toString() else ""
                            Range.YEAR -> date.month.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        }
                    },
                )
            }
            MonthRows(today)
        }
    }
}

/** This month and the two before it, each as average steps a day (days with steps only). */
@Composable
private fun MonthRows(today: LocalDate) {
    val repo = LocalContext.current.strideApp.stepRepository
    val thisMonth = YearMonth.from(today)
    val first = thisMonth.minusMonths(2).atDay(1)
    val days by remember(first, today) { repo.observeRange(first.toEpochDay(), today.toEpochDay()) }
        .collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L..2L).forEach { back ->
            val month = thisMonth.minusMonths(back)
            val avg = averageSteps(days.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }) ?: 0L
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (back == 0L) "This month" else month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                )
                Text(String.format(Locale.getDefault(), "%,d", avg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Average steps a day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BodyCard(p: UserProfile) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(String.format(Locale.getDefault(), "%.1f", p.bmi), "BMI")
            Stat(String.format(Locale.getDefault(), "%.0f", p.bmr), "BMR")
            Stat(String.format(Locale.getDefault(), "%.2f m", p.strideM), "stride")
        }
    }
}
