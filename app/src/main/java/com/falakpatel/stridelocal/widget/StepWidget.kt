package com.falakpatel.stridelocal.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.health.DayRings
import com.falakpatel.stridelocal.sensor.StepCounterService
import com.falakpatel.stridelocal.sensor.StepSync
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

private const val WHITE = 0xFFEDEDED.toInt()
private const val GREY = 0xFF9E9E9E.toInt()
private val Track = ColorProvider(Color(0xFF1C1C1C))

private fun fmtInt(n: Double) = String.format(Locale.getDefault(), "%,d", n.toLong())
private fun fmtKm(km: Double) = String.format(Locale.getDefault(), "%.2f", km)

/** What a widget shows. Each widget remembers its own choice (long-press it, then "Widget settings"). */
enum class WidgetMetric(val title: String) {
    STEPS("Steps"),
    DISTANCE("Distance"),
    DEFICIT("Calorie deficit");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: STEPS
    }
}

/** Ask every padachar widget to redraw (after food is added, for example). */
suspend fun refreshWidgets(context: Context) {
    runCatching { StepWidget().updateAll(context) }
}

/**
 * Home screen widget (Jetpack Glance), pure black with your accent colour, text in Exo 2.
 * Shows steps, distance or calorie deficit, picked per widget in its settings.
 * Resizes from 1x1 up: 1x1 shows the number, 2x1 a slim bar, 1x2 a stacked column, 2x2 the full card.
 * Reads straight from the local Room database; the service pushes updates at most every 30 s.
 */
class StepWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(TINY, WIDE, TALL, BIG))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.strideApp
        val initialDay = app.stepRepository.today()
        val initialProfile = app.userPreferences.profile.first()
        val initialFood = app.stepRepository.observeFoodToday().first()

        provideContent {
            // While the widget session is alive, these flows keep it live without extra work.
            val day by app.stepRepository.observeToday().collectAsState(initialDay)
            val profile by app.userPreferences.profile.collectAsState(initialProfile)
            val food by app.stepRepository.observeFoodToday().collectAsState(initialFood)
            val metric = WidgetMetric.from(currentState<Preferences>()[METRIC])
            WidgetContent(shown(metric, day, profile, food.sumOf { it.kcal }), Color(profile.accentArgb))
        }
    }

    /** All the text and the bar for one metric, so the layouts below are the same for all three. */
    private class Shown(
        val title: String,     // 2x2 heading
        val number: String,    // the big number, no unit (1x1, 1x2)
        val headline: String,  // the big number with its unit where useful (2x1, 2x2)
        val unit: String,      // under the number in 1x2
        val tag: String,       // small accent word: percent, or deficit / surplus
        val progress: Float,   // the bar, 0 to 1
        val left: String,      // 2x2 bottom row
        val right: String,
        val foot: String,      // 2x1 bottom line
    )

    private fun shown(metric: WidgetMetric, day: DailySteps, p: UserProfile, eatenKcal: Double): Shown {
        val g = DayRings.of(day, p, eatenKcal)
        val steps = fmtInt(day.steps.toDouble())
        val km = fmtKm(day.distanceKm)
        return when (metric) {
            WidgetMetric.STEPS -> Shown(
                title = "Steps today", number = steps, headline = steps, unit = "steps",
                tag = percent(g.steps), progress = g.steps,
                left = "${percent(g.steps)} of ${fmtInt(p.dailyGoal.toDouble())}", right = "$km km",
                foot = "$km km",
            )
            WidgetMetric.DISTANCE -> Shown(
                title = "Distance today", number = km, headline = "$km km", unit = "km",
                tag = percent(g.distance), progress = g.distance,
                left = "${percent(g.distance)} of ${String.format(Locale.getDefault(), "%.1f", g.distanceGoalKm)} km",
                right = "$steps steps",
                foot = "$steps steps",
            )
            WidgetMetric.DEFICIT -> {
                // Same sum as the Food tab: kcal used today (BMR + active) minus kcal eaten.
                val burned = p.bmr + day.activeKcal
                val deficit = burned - eatenKcal
                val kcal = fmtInt(abs(deficit))
                Shown(
                    title = if (deficit >= 0) "Deficit today" else "Surplus today",
                    number = kcal, headline = "$kcal kcal", unit = "kcal",
                    tag = if (deficit >= 0) "deficit" else "surplus",
                    progress = g.eaten, // the bar: kcal eaten of today's food target
                    left = "${fmtInt(eatenKcal)} eaten", right = "${fmtInt(burned)} burned",
                    foot = "${fmtInt(eatenKcal)} of ${fmtInt(g.eatTargetKcal)} eaten",
                )
            }
        }
    }

    private fun percent(f: Float) = "${(f * 100).toInt()}%"

    @Composable
    private fun WidgetContent(s: Shown, accent: Color) {
        val size = LocalSize.current
        val wide = size.width >= 110.dp
        val tall = size.height >= 100.dp
        val accentArgb = accent.toArgb()

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
                        Box(GlanceModifier.defaultWeight()) { ExoText(s.title, 12f, GREY) }
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh), contentDescription = "Refresh",
                            colorFilter = ColorFilter.tint(ColorProvider(Color(WHITE))),
                            modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                        )
                    }
                    ExoText(s.headline, 30f, WHITE, bold = true)
                    Bar(s.progress, accent)
                    Row(GlanceModifier.fillMaxWidth()) {
                        Box(GlanceModifier.defaultWeight()) { ExoText(s.left, 12f, GREY) }
                        ExoText(s.right, 12f, GREY)
                    }
                }
                wide -> { // 2x1: one slim row
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.defaultWeight()) { ExoText(s.headline, 20f, WHITE, bold = true) }
                        ExoText(s.tag, 13f, accentArgb, bold = true)
                    }
                    Bar(s.progress, accent)
                    ExoText(s.foot, 12f, GREY)
                }
                tall -> { // 1x2: stacked
                    ExoText(s.number, 17f, WHITE, bold = true)
                    ExoText(s.unit, 12f, GREY)
                    Bar(s.progress, accent)
                    ExoText(s.tag, 13f, accentArgb, bold = true)
                }
                else -> { // 1x1
                    ExoText(s.number, 15f, WHITE, bold = true)
                    ExoText(s.tag, 12f, accentArgb)
                }
            }
        }
    }

    @Composable
    private fun Bar(progress: Float, accent: Color) {
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress, modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(accent), backgroundColor = Track,
        )
        Spacer(GlanceModifier.height(4.dp))
    }

    /**
     * Text in the app's font (Exo 2). Widgets cannot load a font file themselves,
     * so the text is drawn into a small picture and shown as an image.
     */
    @Composable
    private fun ExoText(text: String, sizeSp: Float, argb: Int, bold: Boolean = false) {
        if (text.isEmpty()) return
        val context = LocalContext.current
        val metrics = context.resources.displayMetrics
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = context.resources.getFont(if (bold) R.font.exo2_bold else R.font.exo2_medium)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            color = argb
        }
        val fm = paint.fontMetrics
        val w = ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
        val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -fm.ascent, paint)
        Image(
            provider = ImageProvider(bitmap), contentDescription = text,
            modifier = GlanceModifier.size((w / metrics.density).dp, (h / metrics.density).dp),
        )
    }

    companion object {
        /** Key for this widget's choice, kept in the widget's own Glance state. */
        val METRIC = stringPreferencesKey("metric")

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
