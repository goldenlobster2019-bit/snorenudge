package ro.goldenlobster.snorenudge.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Brand palette: near-black navy, muted teal for snoring/active, amber for nudges/warnings. */
object Palette {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF151B26)
    val SurfaceHigh = Color(0xFF1D2533)
    val Outline = Color(0xFF2A3342)
    val Teal = Color(0xFF4FD1C5)
    val TealMuted = Color(0xFF2F8C85)
    val TealDeep = Color(0xFF123B3A)
    val Amber = Color(0xFFF5B544)
    val AmberDeep = Color(0xFF3A2C12)
    val TextPrimary = Color(0xFFE6EAF0)
    val TextSecondary = Color(0xFF9AA4B2)
    val Danger = Color(0xFFE8908A)
    val DangerDeep = Color(0xFF3A1F1F)
}

// Dark only.
private val SnoreColors = ColorScheme(
    primary = Palette.Teal,
    onPrimary = Color(0xFF06201D),
    primaryContainer = Palette.TealDeep,
    onPrimaryContainer = Color(0xFFBDF2EC),
    primaryDim = Palette.TealMuted,
    secondary = Palette.Amber,
    onSecondary = Color(0xFF2A1D03),
    secondaryContainer = Palette.AmberDeep,
    onSecondaryContainer = Color(0xFFFFDDA0),
    tertiary = Palette.Amber,
    onTertiary = Color(0xFF2A1D03),
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    onSurface = Palette.TextPrimary,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Danger,
    onError = Color(0xFF2B0A0A),
    errorContainer = Palette.DangerDeep,
    onErrorContainer = Color(0xFFF2CFCF),
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
)

@Composable
fun SnoreNudgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SnoreColors, content = content)
}

/** Colors for the history charts. */
object ChartColors {
    val Card = Palette.Surface
    val Snoring = Palette.Teal
    val SnoringMuted = Palette.TealMuted
    val SnoringDim = Palette.TealDeep
    val NudgeL1 = Color(0xFFF7D28A)
    val NudgeL2 = Palette.Amber
    val NudgeL3 = Color(0xFFE08F1F)
    val DelayShade = Color(0xFF1A2232)
    val Axis = Palette.Outline
    val Grid = Color(0xFF1F2736)
    val Label = Palette.TextSecondary

    fun nudge(level: Int): Color = when (level) {
        1 -> NudgeL1
        2 -> NudgeL2
        else -> NudgeL3
    }
}
