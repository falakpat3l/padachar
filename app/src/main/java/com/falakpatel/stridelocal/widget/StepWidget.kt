package com.falakpatel.stridelocal.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.sensor.StepCounterService
import com.falakpatel.stridelocal.sensor.StepSync
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import java.util.Locale

private val White = ColorProvider(Color(0xFFEDEDED))
private val Grey = ColorProvider(Color(0xFF9E9E9E))
private val Track = ColorProvider(Color(0xFF1C1C1C))

private fun fmtSteps(n: Long) = String.format(Locale.getDefault(), "%,d", n)
private fun fmtKm(km: Double) = String.format(Locale.getDefault(), "%.2f km", km)

/**
 * Home screen widget (Jetpack Glance), pure black with your accent colour.
 * Resizes from 1x1 up: 1x1 shows steps, 2x1 a slim bar, 1x2 a stacked column, 2x2 the full card.
 * Reads straight from the local Room database; the service pushes updates at most every 30 s.
 */
class StepWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(TINY, WIDE, TALL, BIG))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.strideApp
        val initialDay = app.stepRepository.today()
        val initialProfile = app.userPreferences.profile.first()

        provideContent {
            // While the widget session is alive, these flows keep it live without extra work.
            val day by app.stepRepository.observeToday().collectAsState(initialDay)
            val profile by app.userPreferences.profile.collectAsState(null)
            val p = profile ?: initialProfile
            WidgetContent(day, p.dailyGoal, ColorProvider(Color(p.accentArgb)))
        }
    }

    @Composable
    private fun WidgetContent(day: DailySteps, goal: Int, accent: ColorProvider) {
        val size = LocalSize.current
        val wide = size.width >= 110.dp
        val tall = size.height >= 100.dp
        val progress = if (goal > 0) (day.steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
        val percent = "${(progress * 100).toInt()}%"

        Column(
            modifier = GlanceModifier.fillMaxSize().background(Color.Black).cornerRadius(18.dp)
                .padding(horizontal = if (wide) 12.dp else 6.dp, vertical = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            when {
                wide && tall -> { // 2x2 and bigger: full card with refresh button
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Steps today", style = small(), modifier = GlanceModifier.defaultWeight())
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh), contentDescription = "Refresh",
                            colorFilter = ColorFilter.tint(White),
                            modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                        )
                    }
                    Text(fmtSteps(day.steps), style = big(30.sp))
                    Bar(progress, accent)
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text("$percent of ${fmtSteps(goal.toLong())}", style = small(), modifier = GlanceModifier.defaultWeight())
                        Text(fmtKm(day.distanceKm), style = small())
                    }
                }
                wide -> { // 2x1: one slim row
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtSteps(day.steps), style = big(20.sp), modifier = GlanceModifier.defaultWeight())
                        Text(percent, style = TextStyle(color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                    Bar(progress, accent)
                    Text(fmtKm(day.distanceKm), style = small())
                }
                tall -> { // 1x2: stacked
                    Text(fmtSteps(day.steps), style = big(17.sp))
                    Text("steps", style = small())
                    Bar(progress, accent)
                    Text(percent, style = TextStyle(color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Text(String.format(Locale.getDefault(), "%.1f km", day.distanceKm), style = small())
                }
                else -> { // 1x1
                    Text(fmtSteps(day.steps), style = big(15.sp))
                    Text(percent, style = TextStyle(color = accent, fontSize = 12.sp))
                }
            }
        }
    }

    @Composable
    private fun Bar(progress: Float, accent: ColorProvider) {
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(progress = progress, modifier = GlanceModifier.fillMaxWidth().height(6.dp), color = accent, backgroundColor = Track)
        Spacer(GlanceModifier.height(4.dp))
    }

    private fun small() = TextStyle(color = Grey, fontSize = 12.sp)
    private fun big(size: TextUnit) = TextStyle(color = White, fontSize = size, fontWeight = FontWeight.Bold)

    companion object {
        private val TINY = DpSize(57.dp, 50.dp)
        private val WIDE = DpSize(130.dp, 50.dp)
        private val TALL = DpSize(57.dp, 110.dp)
        private val BIG = DpSize(130.dp, 110.dp)
    }
}

/** Refresh button: pull the latest count now, and restart tracking if Android stopped it. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!StepCounterService.isRunning) StepCounterService.start(context) // widget taps may restart it
        StepSync.syncNow(context)
    }
}
