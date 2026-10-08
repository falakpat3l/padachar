package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.falakpatel.stridelocal.R

// Colours from falakpatel.com (dark mode), on a pitch black background.
private val Text = Color(0xFFE6E6E6)
private val Muted = Color(0xFF9E9E9E)
private val Line = Color(0xFF262626)  // thin outlines, tracks, gridlines

/** The website's link blue. The default accent for new installs. */
const val WEBSITE_BLUE = 0xFF8AB4F8

/** Accent choices in Settings: one short row. */
val AccentPresets = listOf(
    WEBSITE_BLUE, 0xFFFF453A, 0xFFFF9F0A, 0xFF30D158, 0xFF00C2A8, 0xFFBF5AF2, 0xFFE6E6E6,
).map { Color(it) }

/** Second ring colour: the accent rotated 40 degrees around the colour wheel (third ring: twice). */
fun Color.companion(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Color.hsv((hsv[0] + 40f) % 360f, hsv[1].coerceAtLeast(0.35f), hsv[2])
}

/**
 * Exo 2, used for all text in the app (SIL Open Font License, see docs/fonts).
 * Bundled in res/font, so it looks the same on every phone and works offline.
 */
val AppFont = FontFamily(
    Font(R.font.exo2_regular, FontWeight.Normal),
    Font(R.font.exo2_medium, FontWeight.Medium),
    Font(R.font.exo2_semibold, FontWeight.SemiBold),
    Font(R.font.exo2_bold, FontWeight.Bold),
)

// Headings and short text are centred; bodyLarge (used by text boxes) stays left aligned.
private fun TextStyle.appStyle(center: Boolean = true) =
    copy(fontFamily = AppFont, textAlign = if (center) TextAlign.Center else TextAlign.Unspecified)

private val Base = Typography()
private val SerifType = Typography(
    displayLarge = Base.displayLarge.appStyle(),
    displayMedium = Base.displayMedium.appStyle(),
    displaySmall = Base.displaySmall.appStyle(),
    headlineLarge = Base.headlineLarge.appStyle(),
    headlineMedium = Base.headlineMedium.appStyle(),
    headlineSmall = Base.headlineSmall.appStyle(),
    titleLarge = Base.titleLarge.appStyle(),
    titleMedium = Base.titleMedium.appStyle(),
    titleSmall = Base.titleSmall.appStyle(),
    bodyLarge = Base.bodyLarge.appStyle(center = false),
    bodyMedium = Base.bodyMedium.appStyle(),
    bodySmall = Base.bodySmall.appStyle(),
    labelLarge = Base.labelLarge.appStyle(center = false),
    labelMedium = Base.labelMedium.appStyle(center = false),
    labelSmall = Base.labelSmall.appStyle(center = false),
)

// Small, square-ish corners like the website's buttons.
private val SmallCorners = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/** Pitch black everywhere, thin grey lines to separate things, one accent colour. */
@Composable
fun StrideTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent, onPrimary = Color.Black,
        primaryContainer = Line, onPrimaryContainer = Text,
        secondary = accent.companion(), onSecondary = Color.Black,
        secondaryContainer = Line, onSecondaryContainer = Text,
        tertiary = Muted,
        background = Color.Black, onBackground = Text,
        surface = Color.Black, onSurface = Text,
        surfaceVariant = Line, onSurfaceVariant = Muted,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black, surfaceContainerHigh = Color(0xFF0E0E0E), surfaceContainerHighest = Color(0xFF0E0E0E),
        outline = Color(0xFF3A3A3A), outlineVariant = Line,
        errorContainer = Color(0xFF2A1010), onErrorContainer = Color(0xFFFFB4AB),
    )
    MaterialTheme(colorScheme = scheme, typography = SerifType, shapes = SmallCorners, content = content)
}
