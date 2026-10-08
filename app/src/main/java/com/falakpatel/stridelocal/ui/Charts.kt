package com.falakpatel.stridelocal.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falakpatel.stridelocal.data.DailySteps
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as DayNameStyle

/** One coloured piece of a bar. A stacked bar has several (walking, then running on top). */
class BarPart(val value: Long, val color: Color)

/** Short step count for chart labels: 950 -> "950", 8234 -> "8.2k", 12400 -> "12k". */
fun compactSteps(n: Long): String = when {
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", n / 1000.0)
    else -> n.toString()
}

/**
 * Google Fit style bar chart of steps per day.
 *
 *  - Rounded "pill" bars that grow in when the chart first appears.
 *  - Each bar's total is written small, inside the bottom of the bar (or just above
 *    a bar too short to hold it). If the step number is too wide, it shows km instead.
 *  - Tap or drag across the bars to pick a day. A thin line with a ring at the top marks
 *    the picked day and glides from bar to bar, passing each one in between.
 *  - [header] shows the picked day's numbers above the chart.
 *  - Y axis on the right (steps); x axis along the bottom (day of the week).
 *  - [goal], if given, is a coloured line labelled with the goal.
 *  - [average], if given, is a dashed line labelled "avg".
 *  - [labelFor] names each bar on the x axis (empty = no label, for crowded charts).
 *  - A light tick (haptic) each time the marker moves to another bar.
 */
@Composable
fun StepBarChart(
    days: List<DailySteps>,
    parts: (DailySteps) -> List<BarPart>,
    header: @Composable (DailySteps) -> Unit,
    caption: String,
    goal: Int? = null,
    average: Long? = null,
    labelFor: (index: Int, day: DailySteps) -> String = { _, d ->
        LocalDate.ofEpochDay(d.epochDay).dayOfWeek.getDisplayName(DayNameStyle.SHORT, Locale.getDefault())
    },
) {
    if (days.isEmpty()) return
    // A new range (other dates or another number of bars) starts again on its last bar.
    val rangeKey = days.first().epochDay to days.size
    var selected by remember(rangeKey) { mutableIntStateOf(days.lastIndex) }
    val haptic = LocalHapticFeedback.current
    fun pick(i: Int) {
        if (i != selected) {
            selected = i
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // The marker's position, animated between bars (a float, so it passes the bars in between).
    val marker by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "marker",
    )
    // Bars grow from 0 to full height when the chart appears or the range changes.
    val grow = remember(rangeKey) { Animatable(0f) }
    LaunchedEffect(rangeKey) { grow.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing)) }

    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.primary
    val axisStyle = TextStyle(fontSize = 10.sp, fontFamily = AppFont, color = muted)
    val inBarStyle = TextStyle(fontSize = 10.sp, fontFamily = AppFont, fontWeight = FontWeight.SemiBold, color = Color.Black)
    val top = maxOf(days.maxOf { it.steps }, (goal ?: 0).toLong(), 1L)

    // Which bar is under a finger at x (the chart area leaves room on the right for the axis).
    fun indexAt(x: Float, width: Float, axisPx: Float): Int {
        val slot = (width - axisPx) / days.size
        return (x / slot).toInt().coerceIn(0, days.lastIndex)
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header(days[selected.coerceIn(0, days.lastIndex)])
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(rangeKey) {
                    detectTapGestures { tap -> pick(indexAt(tap.x, size.width.toFloat(), 40.dp.toPx())) }
                }
                .pointerInput(rangeKey) {
                    detectHorizontalDragGestures { change, _ ->
                        pick(indexAt(change.position.x, size.width.toFloat(), 40.dp.toPx()))
                    }
                },
        ) {
            val axis = 40.dp.toPx()                  // right-hand room for the step labels
            val chartRight = size.width - axis
            val bottom = size.height - 20.dp.toPx()   // room for day names
            val chartTop = 14.dp.toPx()
            val chartH = bottom - chartTop
            val slot = chartRight / days.size
            val barW = slot * 0.62f
            fun yFor(v: Long) = bottom - chartH * v / top
            fun barHeight(d: DailySteps) = chartH * d.steps / top * grow.value

            // Gridlines with step labels on the right: 0, half, top.
            listOf(0L, top / 2, top).forEach { v ->
                val y = yFor(v)
                drawLine(gridColor, Offset(0f, y), Offset(chartRight, y), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(compactSteps(v), axisStyle)
                drawText(label, topLeft = Offset(chartRight + 6.dp.toPx(), y - label.size.height / 2f))
            }

            // Goal line in the accent colour, labelled on the right.
            if (goal != null && goal > 0) {
                val y = yFor(goal.toLong())
                drawLine(goalColor.copy(alpha = 0.7f), Offset(0f, y), Offset(chartRight, y), strokeWidth = 1.5.dp.toPx())
                val label = measurer.measure(compactSteps(goal.toLong()), axisStyle.copy(color = goalColor))
                drawText(label, topLeft = Offset(chartRight + 6.dp.toPx(), y - label.size.height / 2f))
            }

            // Average as a dashed line, labelled "avg" on the right.
            if (average != null && average > 0) {
                val y = yFor(average)
                drawLine(
                    strong.copy(alpha = 0.5f), Offset(0f, y), Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val label = measurer.measure("avg", axisStyle)
                drawText(label, topLeft = Offset(chartRight + 6.dp.toPx(), y - label.size.height / 2f))
            }

            // Pill-shaped bars. Stacked parts are drawn inside one pill outline.
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2
                val h = barHeight(d)
                if (h > 0f) {
                    val radius = minOf(barW / 2, h / 2)
                    val pill = Path().apply {
                        addRoundRect(RoundRect(x, bottom - h, x + barW, bottom, CornerRadius(radius)))
                    }
                    clipPath(pill) {
                        var base = bottom
                        parts(d).forEach { p ->
                            val partH = if (d.steps > 0) h * p.value / d.steps else 0f
                            if (partH > 0f) {
                                drawRect(p.color, Offset(x, base - partH), Size(barW, partH))
                                base -= partH
                            }
                        }
                    }
                }
                // Day name under the bar.
                val name = labelFor(i, d)
                if (name.isNotEmpty()) {
                    val day = measurer.measure(name, axisStyle.copy(color = if (i == selected) strong else muted))
                    drawText(day, topLeft = Offset(x + barW / 2 - day.size.width / 2f, bottom + 5.dp.toPx()))
                }
            }

            // The marker: a line from the top of the chart down to a ring on the bar's top,
            // at a position between bars while it moves.
            val lo = marker.toInt().coerceIn(0, days.lastIndex)
            val hi = (lo + 1).coerceAtMost(days.lastIndex)
            val t = marker - lo
            val markerX = (marker + 0.5f) * slot
            val markerTop = bottom - (barHeight(days[lo]) * (1 - t) + barHeight(days[hi]) * t)
            val ring = 5.dp.toPx()
            drawLine(strong.copy(alpha = 0.8f), Offset(markerX, chartTop - 6.dp.toPx()), Offset(markerX, markerTop - ring), strokeWidth = 1.5.dp.toPx())
            // Inside the bar the line stops above the total written at its bottom.
            val innerEnd = bottom - 24.dp.toPx()
            if (innerEnd > markerTop + ring) {
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(markerX, markerTop + ring), Offset(markerX, innerEnd), strokeWidth = 1.5.dp.toPx())
            }
            drawCircle(Color.Black, radius = ring, center = Offset(markerX, markerTop))
            drawCircle(strong, radius = ring, center = Offset(markerX, markerTop), style = Stroke(width = 2.dp.toPx()))

            // Totals: small and dark inside the bottom of each bar, or muted just above a short bar.
            days.forEachIndexed { i, d ->
                if (d.steps <= 0L) return@forEachIndexed
                val x = i * slot + (slot - barW) / 2
                val h = barHeight(d)
                var text = compactSteps(d.steps)
                if (measurer.measure(text, inBarStyle).size.width > barW - 4.dp.toPx()) {
                    text = String.format(Locale.getDefault(), "%.1f", d.distanceKm) // km, shorter
                }
                val inside = measurer.measure(text, inBarStyle)
                if (inside.size.width > barW + 2.dp.toPx()) return@forEachIndexed  // bars too thin (month view): no numbers
                if (h >= inside.size.height + 10.dp.toPx()) {
                    drawText(inside, topLeft = Offset(x + barW / 2 - inside.size.width / 2f, bottom - inside.size.height - 5.dp.toPx()))
                } else {
                    val above = measurer.measure(text, axisStyle)
                    drawText(above, topLeft = Offset(x + barW / 2 - above.size.width / 2f, bottom - h - above.size.height - 2.dp.toPx()))
                }
            }
        }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}
