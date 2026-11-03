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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.ui.theme.Palette

/** Small decorative icon; the adjacent text carries the meaning, so no content description. */
@Composable
fun GlyphIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Icon(imageVector = icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/**
 * List state for screens that start with a [ScreenHeader]: the header is the initial center item, nudged
 * upward so it clears the TimeText and the content below stays visible. Other indexes are centered as is.
 */
@Composable
fun rememberHeaderListState(initialCenterItemIndex: Int = 0): ScalingLazyListState {
    val offsetPx = with(LocalDensity.current) { HEADER_LIFT.roundToPx() }
    return rememberScalingLazyListState(
        initialCenterItemIndex = initialCenterItemIndex,
        initialCenterItemScrollOffset = if (initialCenterItemIndex == 0) offsetPx else 0,
    )
}

private val HEADER_LIFT = 62.dp

/** Screen header: icon above title (titleMedium) with an optional secondary subtitle, centered. */
@Composable
fun ScreenHeader(icon: ImageVector, title: String, subtitle: String? = null, tint: Color = Palette.Teal) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 2.dp),
    ) {
        GlyphIcon(icon, tint, size = 20.dp)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
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

/** Value tile: icon, value (with unit) and small label. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Teal,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .padding(horizontal = 3.dp, vertical = 8.dp),
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
            label,
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

/**
 * Full-width tonal button whose icon + label (+ optional secondary line) group is centered
 * horizontally; the icon stays to the left of the text.
 */
@Composable
fun CenteredTonalButton(
    onClick: () -> Unit,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: androidx.compose.foundation.BorderStroke? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = colors,
        border = border,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(icon, iconTint, size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.contentColor, textAlign = TextAlign.Center, maxLines = 1)
                if (secondaryLabel != null) {
                    Text(
                        secondaryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryContentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
