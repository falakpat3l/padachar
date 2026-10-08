package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
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
 * Bar chart of steps per day.
 *   Y axis (left): number of steps, with lines at 0, half and the top value.
 *   X axis (bottom): day of the week.
 *   The number above each bar is that day's steps (or its distance in km when the
 *   step number is too wide to fit). Tap a bar to see its details
 *   underneath. Today is selected until something else is tapped.
 *
 * [parts] says how to colour each bar; [details] is the line shown for the tapped day;
 * [goal] draws a dashed line at the daily step goal.
 */
@Composable
fun StepBarChart(
    days: List<DailySteps>,
    parts: (DailySteps) -> List<BarPart>,
    details: (DailySteps) -> String,
    caption: String,
    goal: Int? = null,
) {
    if (days.isEmpty()) return
    var selected by remember(days.size) { mutableIntStateOf(days.lastIndex) }
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val small = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Serif, color = muted)
    val top = maxOf(days.maxOf { it.steps }, (goal ?: 0).toLong(), 1L)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(days.size) {
                    detectTapGestures { tap ->
                        val left = 34.dp.toPx()
                        val slot = (size.width - left) / days.size
                        selected = ((tap.x - left) / slot).toInt().coerceIn(0, days.lastIndex)
                    }
                },
        ) {
            val left = 34.dp.toPx()                 // room for step numbers (y axis)
            val bottom = size.height - 18.dp.toPx() // room for day names (x axis)
            val chartTop = 16.dp.toPx()             // room for the number above the tallest bar
            val chartH = bottom - chartTop
            fun yFor(v: Long) = bottom - chartH * v / top

            // Y axis: three gridlines, each labelled with its step count.
            listOf(0L, top / 2, top).forEach { v ->
                val y = yFor(v)
                drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(compactSteps(v), small)
                drawText(label, topLeft = Offset(left - label.size.width - 4.dp.toPx(), y - label.size.height / 2f))
            }

            // Daily goal as a dashed line.
            if (goal != null && goal > 0) {
                val y = yFor(goal.toLong())
                drawLine(
                    goalColor, Offset(left, y), Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }

            // Bars, with the total above and the day name below.
            val slot = (size.width - left) / days.size
            val barW = slot * 0.55f
            days.forEachIndexed { i, d ->
                val isSelected = i == selected
                val x = left + i * slot + (slot - barW) / 2
                var base = bottom
                parts(d).forEach { p ->
                    val h = chartH * p.value / top
                    if (h > 0f) {
                        drawRoundRect(
                            color = p.color.copy(alpha = if (isSelected) 1f else 0.55f),
                            topLeft = Offset(x, base - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                        base -= h
                    }
                }
                val labelStyle = small.copy(color = if (isSelected) strong else muted)
                // Steps above the bar; if that number is too wide for the bar's slot,
                // show the day's distance in km instead (shorter).
                var total = measurer.measure(compactSteps(d.steps), labelStyle)
                if (total.size.width > slot * 0.95f) {
                    total = measurer.measure(String.format(Locale.getDefault(), "%.1f km", d.distanceKm), labelStyle)
                    if (total.size.width > slot * 0.95f) {
                        total = measurer.measure(String.format(Locale.getDefault(), "%.1f", d.distanceKm), labelStyle)
                    }
                }
                drawText(total, topLeft = Offset(x + barW / 2 - total.size.width / 2f, base - total.size.height - 2.dp.toPx()))
                val dayName = LocalDate.ofEpochDay(d.epochDay).dayOfWeek.getDisplayName(DayNameStyle.SHORT, Locale.getDefault())
                val day = measurer.measure(dayName, labelStyle)
                drawText(day, topLeft = Offset(x + barW / 2 - day.size.width / 2f, bottom + 4.dp.toPx()))
            }
        }
        Text(details(days[selected.coerceIn(0, days.lastIndex)]), style = MaterialTheme.typography.bodyMedium)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}
