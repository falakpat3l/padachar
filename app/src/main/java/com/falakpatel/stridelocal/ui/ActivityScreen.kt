package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.sensor.RUN_STEPS_PER_MIN
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Activity tab: walking vs running, told apart by step speed. Every minute with
 * [RUN_STEPS_PER_MIN] or more steps is a running minute; 40 or more is a walking minute.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(state: MainUiState) {
    val walk = MaterialTheme.colorScheme.primary
    val run = MaterialTheme.colorScheme.secondary
    val t = state.today
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Activity") }) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaceCard("Walking", walk, t.walkMin, t.walkSteps, Modifier.weight(1f))
                PaceCard("Running", run, t.runMin, t.runSteps, Modifier.weight(1f))
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Big("${t.walkMin + t.runMin}", "active min")
                    Big(String.format(Locale.getDefault(), "%.2f", t.distanceKm), "km")
                    Big(String.format(Locale.getDefault(), "%.0f", t.activeKcal), "active kcal")
                }
            }
            WeekSplit(state.week, walk, run)
            Text(
                "Run = $RUN_STEPS_PER_MIN+ steps a minute. Walk = 40+.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaceCard(title: String, color: Color, minutes: Int, steps: Long, modifier: Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("$minutes min", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(String.format(Locale.getDefault(), "%,d steps", steps), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Big(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Last 7 days as stacked bars: walking steps at the bottom, running steps on top. */
@Composable
private fun WeekSplit(week: List<DailySteps>, walk: Color, run: Color) {
    if (week.isEmpty()) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            StepBarChart(
                days = week,
                parts = { listOf(BarPart(it.walkSteps, walk), BarPart(it.runSteps.coerceAtMost(it.steps), run)) },
                details = { d ->
                    val day = LocalDate.ofEpochDay(d.epochDay).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    String.format(Locale.getDefault(), "%s: %,d walking, %,d running", day, d.walkSteps, d.runSteps.coerceAtMost(d.steps))
                },
                caption = "Steps per day, walking (bottom) and running (top). Tap a bar for details.",
            )
        }
    }
}
