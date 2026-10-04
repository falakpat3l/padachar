package com.falakpatel.stridelocal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val Text = Color(0xFFEDEDED)
private val Muted = Color(0xFF9E9E9E)
private val Raised = Color(0xFF0E0E0E) // cards: just off black
private val Line = Color(0xFF1E1E1E)  // tracks, dividers

/** Rainbow presets shown in settings (plus a free hue slider). */
val AccentPresets = listOf(
    0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF00C2A8,
    0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFEDEDED,
).map { Color(it) }

/** Second ring colour: the accent rotated 40 degrees around the colour wheel. */
fun Color.companion(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Color.hsv((hsv[0] + 40f) % 360f, hsv[1].coerceAtLeast(0.35f), hsv[2])
}

/** Pitch black everywhere, slight greys only to separate things, one accent colour. */
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
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Raised,
        surfaceContainer = Raised, surfaceContainerHigh = Raised, surfaceContainerHighest = Raised,
        outline = Color(0xFF2A2A2A), outlineVariant = Line,
        errorContainer = Color(0xFF2A1010), onErrorContainer = Color(0xFFFFB4AB),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
