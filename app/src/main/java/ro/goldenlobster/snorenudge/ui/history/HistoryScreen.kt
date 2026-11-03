package ro.goldenlobster.snorenudge.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.data.AppDatabase
import ro.goldenlobster.snorenudge.data.NightSession
import ro.goldenlobster.snorenudge.ui.components.GlyphIcon
import ro.goldenlobster.snorenudge.ui.components.MutedText
import ro.goldenlobster.snorenudge.ui.components.Pill
import ro.goldenlobster.snorenudge.ui.components.ScreenHeader
import ro.goldenlobster.snorenudge.ui.components.rememberHeaderListState
import ro.goldenlobster.snorenudge.ui.components.StatTile
import ro.goldenlobster.snorenudge.ui.components.SurfaceCard
import ro.goldenlobster.snorenudge.ui.theme.ChartColors
import ro.goldenlobster.snorenudge.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val NIGHTS = 30

@Composable
fun HistoryScreen(onNight: (Long) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val flow = remember { AppDatabase.get(ctx).sessions().observeFinished(NIGHTS) }
    val sessions by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    HistoryContent(sessions = sessions, onNight = onNight)
}

/** Stateless History content. [today] only drives which bar is highlighted; injectable for deterministic screenshots. */
@Composable
internal fun HistoryContent(
    sessions: List<NightSession>,
    onNight: (Long) -> Unit,
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
) {
    val listState = rememberHeaderListState()
    // Test-mode runs are listed but kept out of the averages and the chart.
    val real = sessions.filter { !it.testMode }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            autoCentering = null,
        ) {
            item { ScreenHeader(Icons.Rounded.History, "Last 30 nights", "Nightly averages") }
            if (sessions.isEmpty()) {
                item { MutedText("No nights recorded yet") }
            } else {
                item { SummaryCard(real, today) }
                sessions.forEach { s ->
                    item(key = s.id) { NightCard(s) { onNight(s.id) } }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(nights: List<NightSession>, today: LocalDate) {
    SurfaceCard(fraction = 0.96f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val n = nights.size
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    Icons.Rounded.GraphicEq,
                    if (n == 0) "--" else String.format(Locale.US, "%.0f min", nights.sumOf { it.snoringMinutes } / n.toDouble()),
                    "snoring",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    Icons.Rounded.Vibration,
                    if (n == 0) "--" else String.format(Locale.US, "%.1f", nights.sumOf { it.nudges } / n.toDouble()),
                    "nudges",
                    modifier = Modifier.weight(1f),
                    tint = Palette.Amber,
                )
                StatTile(Icons.Rounded.Bedtime, n.toString(), "nights", modifier = Modifier.weight(1f))
            }
            if (n > 0) {
                val chrono = nights.sortedBy { it.startedAt }
                NightsBarChart(chrono, today)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(shortDateLabel(chrono.first().startedAt), style = MaterialTheme.typography.labelSmall, color = ChartColors.Label)
                    Text(shortDateLabel(chrono.last().startedAt), style = MaterialTheme.typography.labelSmall, color = ChartColors.Label)
                }
            }
        }
    }
}

/** Rounded bars, one per night (snoring minutes), oldest left, newest right, in a fixed 30-slot grid. Today is brighter. */
@Composable
private fun NightsBarChart(chrono: List<NightSession>, today: LocalDate, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    Canvas(modifier = modifier.fillMaxWidth().height(52.dp)) {
        val slots = NIGHTS
        val slotW = size.width / slots
        val barW = (slotW * 0.62f).coerceAtLeast(2f)
        val maxV = (chrono.maxOfOrNull { it.snoringMinutes } ?: 0).coerceAtLeast(1)
        val base = size.height - 1.dp.toPx()
        val offset = slots - chrono.size
        // Subtle guide lines.
        for (f in listOf(0.5f, 1f)) {
            val y = base - f * (size.height - 4.dp.toPx())
            drawRect(ChartColors.Grid, Offset(0f, y), Size(size.width, 1f))
        }
        drawRect(ChartColors.Axis, Offset(0f, base), Size(size.width, 1f))
        chrono.forEachIndexed { i, s ->
            val v = s.snoringMinutes
            val h = if (v <= 0) 3.dp.toPx() else (v.toFloat() / maxV * (size.height - 6.dp.toPx())).coerceAtLeast(4.dp.toPx())
            val x = (offset + i) * slotW + (slotW - barW) / 2f
            val endDay = Instant.ofEpochMilli(s.endedAt ?: s.startedAt).atZone(zone).toLocalDate()
            val color = when {
                v <= 0 -> ChartColors.SnoringDim
                endDay == today -> ChartColors.Snoring
                else -> ChartColors.SnoringMuted
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, base - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

@Composable
private fun NightCard(s: NightSession, onClick: () -> Unit) {
    val duration = durationLabel((s.endedAt ?: s.startedAt) - s.startedAt)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dateLabel(s.startedAt), style = MaterialTheme.typography.labelLarge, color = Palette.TextPrimary, maxLines = 1)
                    if (s.testMode) Pill("TEST", icon = Icons.Rounded.Science, containerColor = Palette.AmberDeep, contentColor = Palette.Amber)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Metric(Icons.Rounded.Schedule, "$duration h", Palette.TextSecondary)
                    Metric(Icons.Rounded.GraphicEq, "${s.snoringMinutes}m", Palette.Teal)
                    Metric(Icons.Rounded.Vibration, s.nudges.toString(), Palette.Amber)
                }
            }
            GlyphIcon(Icons.Rounded.ChevronRight, Palette.TextSecondary, modifier = Modifier.width(18.dp), size = 18.dp)
        }
    }
}

@Composable
private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        GlyphIcon(icon, tint, size = 12.dp)
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
