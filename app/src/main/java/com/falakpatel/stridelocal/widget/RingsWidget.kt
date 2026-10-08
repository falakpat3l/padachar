package com.falakpatel.stridelocal.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.health.DayRings
import com.falakpatel.stridelocal.strideApp
import com.falakpatel.stridelocal.ui.companion
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Rings-only widget: the same three rings as the Home screen (steps, kcal burned,
 * kcal eaten) on pitch black, with today's steps in the middle. Tap to open the app.
 * Glance cannot draw arcs itself, so the rings are drawn into a small bitmap.
 */
class RingsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.strideApp
        val initialDay = app.stepRepository.today()
        val initialProfile = app.userPreferences.profile.first()
        val initialFood = app.stepRepository.observeFoodToday().first()

        provideContent {
            val day by app.stepRepository.observeToday().collectAsState(initialDay)
            val profile by app.userPreferences.profile.collectAsState(initialProfile)
            val food by app.stepRepository.observeFoodToday().collectAsState(initialFood)
            Content(day, profile, food.sumOf { it.kcal })
        }
    }

    @Composable
    private fun Content(day: DailySteps, profile: UserProfile, eatenKcal: Double) {
        val size = LocalSize.current
        val side = minOf(size.width, size.height)
        val large = side >= 100.dp
        val rings = DayRings.of(day, profile, eatenKcal)
        val accent = Color(profile.accentArgb)
        val colors = listOf(accent, accent.companion(), accent.companion().companion()).map { it.toArgb() }
        val bitmap = ringsBitmap(listOf(rings.steps, rings.burned, rings.eaten), colors, 360)

        Box(
            modifier = GlanceModifier.fillMaxSize().background(Color.Black).cornerRadius(18.dp).padding(6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Steps, kcal burned and kcal eaten today",
                modifier = GlanceModifier.size(side - 12.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format(Locale.getDefault(), "%,d", day.steps),
                    style = TextStyle(color = ColorProvider(accent), fontSize = if (large) 16.sp else 11.sp, fontWeight = FontWeight.Bold),
                )
                if (large) Text("steps", style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 10.sp))
            }
        }
    }

    companion object {
        private val SMALL = DpSize(57.dp, 57.dp)
        private val LARGE = DpSize(110.dp, 110.dp)
    }
}

class RingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingsWidget()
}

/** Draws concentric progress rings (outermost first) into a square bitmap [px] wide. */
internal fun ringsBitmap(progress: List<Float>, colors: List<Int>, px: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = px * 0.055f   // thin rings, same proportions as the Home screen
    val step = stroke * 1.3f    // centre-to-centre distance between rings
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    progress.forEachIndexed { i, p ->
        val inset = stroke / 2 + i * step
        val rect = RectF(inset, inset, px - inset, px - inset)
        paint.color = 0xFF262626.toInt()
        canvas.drawArc(rect, 0f, 360f, false, paint)
        if (p > 0f) {
            paint.color = colors[i]
            canvas.drawArc(rect, -90f, 360f * p.coerceIn(0f, 1f), false, paint)
        }
    }
    return bitmap
}
