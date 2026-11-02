package ro.goldenlobster.snorenudge.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// Dark only, soft tones, no bright white.
private val SnoreColors = ColorScheme(
    primary = Color(0xFF9FB4FF),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF26335F),
    onPrimaryContainer = Color(0xFFD5DEFF),
    secondary = Color(0xFF8FC7B8),
    onSecondary = Color(0xFF07201A),
    secondaryContainer = Color(0xFF1F3A33),
    onSecondaryContainer = Color(0xFFCDEBE2),
    surfaceContainer = Color(0xFF171B2A),
    surfaceContainerHigh = Color(0xFF1E2336),
    onSurface = Color(0xFFD0D4E2),
    onSurfaceVariant = Color(0xFF9AA0B5),
    outline = Color(0xFF444A60),
    error = Color(0xFFE08A8A),
    onError = Color(0xFF2B0A0A),
    errorContainer = Color(0xFF5A2323),
    onErrorContainer = Color(0xFFF2CFCF),
    background = Color.Black,
    onBackground = Color(0xFFD0D4E2),
)

@Composable
fun SnoreNudgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SnoreColors, content = content)
}

/** Calm dark palette for the history charts. */
object ChartColors {
    val Card = Color(0xFF131A2C)
    val Snoring = Color(0xFF5FA8A0)
    val SnoringDim = Color(0xFF2F5652)
    val NudgeL1 = Color(0xFFF0CF8A)
    val NudgeL2 = Color(0xFFE8A33D)
    val NudgeL3 = Color(0xFFD9692B)
    val DelayShade = Color(0xFF1C2540)
    val Axis = Color(0xFF3A425C)
    val Label = Color(0xFF9AA0B5)

    fun nudge(level: Int): Color = when (level) {
        1 -> NudgeL1
        2 -> NudgeL2
        else -> NudgeL3
    }
}
