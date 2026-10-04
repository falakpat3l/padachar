package com.falakpatel.stridelocal.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.sensor.StepCounterService
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Home screen widget built with Jetpack Glance.
 * Reads straight from the local Room database. The service pushes updates at most every
 * 30 s while walking (plus a 30 min system safety refresh), which keeps battery cost tiny.
 */
class StepWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.strideApp
        val initialDay = app.stepRepository.today()
        val initialGoal = app.userPreferences.profile.first().dailyGoal

        provideContent {
            // While the widget session is alive, these flows keep it live without extra work.
            val day by app.stepRepository.observeToday().collectAsState(initialDay)
            val profile by app.userPreferences.profile.collectAsState(null)
            GlanceTheme {
                WidgetContent(day, profile?.dailyGoal ?: initialGoal)
            }
        }
    }

    @Composable
    private fun WidgetContent(day: DailySteps, goal: Int) {
        val progress = if (goal > 0) (day.steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
        val percent = if (goal > 0) (day.steps * 100 / goal) else 0
        val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Steps today", style = muted, modifier = GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
            Text(
                text = String.format(Locale.getDefault(), "%,d", day.steps),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.secondaryContainer,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text("$percent% of ${String.format(Locale.getDefault(), "%,d", goal)}", style = muted, modifier = GlanceModifier.defaultWeight())
                Text(String.format(Locale.getDefault(), "%.2f km", day.distanceKm), style = muted)
            }
        }
    }
}

/** Refresh button: ask the service to write its latest count now, or (re)start it if it died. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (StepCounterService.isRunning) StepCounterService.requestRefresh(context)
        else StepCounterService.start(context)
        StepWidget().update(context, glanceId)
    }
}
