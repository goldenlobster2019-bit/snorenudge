package ro.goldenlobster.snorenudge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.ui.theme.Palette

/** Small decorative icon; the adjacent text carries the meaning, so no content description. */
@Composable
fun GlyphIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Icon(imageVector = icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/** Screen title: small icon above a short label, centered. */
@Composable
fun ScreenHeader(icon: ImageVector, title: String, tint: Color = Palette.Teal) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 2.dp)) {
        GlyphIcon(icon, tint, size = 20.dp)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionLabel(icon: ImageVector, text: String, tint: Color = Palette.TextSecondary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        GlyphIcon(icon, tint, size = 14.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
    }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** Rounded surface card used for summaries and charts. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    fraction: Float = 0.88f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { content() }
}

/** Small rounded pill with optional leading icon. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = Palette.SurfaceHigh,
    contentColor: Color = Palette.TextSecondary,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) GlyphIcon(icon, contentColor, size = 12.dp)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Value tile: icon, big value, small caption. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Teal,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        GlyphIcon(icon, tint, size = 16.dp)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Horizontal meter bar. [marker] (0..1) draws a thin tick, e.g. the noise floor. */
@Composable
fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Palette.SurfaceHigh,
    marker: Float? = null,
    height: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        if (marker != null) {
            val m = marker.coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                if (m > 0f) Box(Modifier.fillMaxWidth(m).fillMaxHeight())
                Box(Modifier.width(2.dp).fillMaxHeight().background(Palette.TextPrimary.copy(alpha = 0.8f)))
            }
        }
    }
}

/** Outlined container used for status rows (permissions, warnings). */
fun Modifier.outlinedRow(color: Color = Palette.Outline): Modifier =
    this.border(1.dp, color, RoundedCornerShape(18.dp))
