package ro.goldenlobster.snorenudge.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.data.Aggregations
import ro.goldenlobster.snorenudge.data.AppDatabase
import ro.goldenlobster.snorenudge.data.Nudge
import ro.goldenlobster.snorenudge.data.SnoreEventEntity
import ro.goldenlobster.snorenudge.ui.components.GlyphIcon
import ro.goldenlobster.snorenudge.ui.components.MutedText
import ro.goldenlobster.snorenudge.ui.components.Pill
import ro.goldenlobster.snorenudge.ui.components.SectionLabel
import ro.goldenlobster.snorenudge.ui.components.StatTile
import ro.goldenlobster.snorenudge.ui.components.SurfaceCard
import ro.goldenlobster.snorenudge.ui.theme.ChartColors
import ro.goldenlobster.snorenudge.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun NightDetailScreen(sessionId: Long) {
    val ctx = LocalContext.current.applicationContext
    val db = remember { AppDatabase.get(ctx) }
    val session by remember(sessionId) { db.sessions().observe(sessionId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val events by remember(sessionId) { db.events().observeForSession(sessionId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val nudges by remember(sessionId) { db.nudges().observeForSession(sessionId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberScalingLazyListState()
    val busiest = remember(session, events) {
        session?.let { s ->
            Aggregations.busiest(
                Aggregations.buckets(s.startedAt, s.endedAt ?: s.startedAt, events.map { it.ts }), 5,
            )
        }.orEmpty()
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val s = session
            if (s == null) {
                item { MutedText("Night not found") }
                return@ScalingLazyColumn
            }
            val end = s.endedAt ?: s.startedAt
            item {
                SurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        GlyphIcon(Icons.Rounded.Bedtime, Palette.Teal, size = 18.dp)
                        Text(dateLabel(s.startedAt), style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
                        Text(
                            "${timeLabel(s.startedAt)} - ${timeLabel(end)}, ${durationLabel(end - s.startedAt)} h",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(endReasonLabel(s.endReason), icon = endReasonIcon(s.endReason))
                            if (s.testMode) {
                                Pill("TEST", icon = Icons.Rounded.Science, containerColor = Palette.AmberDeep, contentColor = Palette.Amber)
                            }
                        }
                    }
                }
            }
            val l1 = nudges.count { it.level == 1 }
            val l2 = nudges.count { it.level == 2 }
            val l3 = nudges.count { it.level >= 3 }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(0.88f)) {
                    StatTile(Icons.Rounded.GraphicEq, s.snoreEvents.toString(), "snore events", Modifier.weight(1f))
                    StatTile(Icons.Rounded.Timer, "${s.snoringMinutes} min", "snoring", Modifier.weight(1f))
                }
            }
            item {
                StatTile(
                    Icons.Rounded.Vibration, s.nudges.toString(), "nudges   L1 $l1 / L2 $l2 / L3 $l3",
                    Modifier.fillMaxWidth(0.88f), tint = Palette.Amber,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(0.88f)) {
                    StatTile(
                        Icons.Rounded.Mic, String.format(Locale.US, "%.0f dB", s.avgNoiseFloorDb), "noise floor",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        Icons.Rounded.Sensors, String.format(Locale.US, "%.0f%%", s.gateOpenRatio * 100f), "gate open",
                        Modifier.weight(1f),
                    )
                }
            }

            val delayMin = if (s.testMode) 0 else Aggregations.startDelayMinutes(s.settingsSummary) ?: 0
            item {
                TimelineCard(
                    start = s.startedAt,
                    end = end,
                    events = events,
                    nudges = nudges,
                    delayMs = delayMin * 60_000L,
                )
            }

            item { SectionLabel(Icons.Rounded.Vibration, "Nudges", Palette.Amber) }
            if (nudges.isEmpty()) {
                item { MutedText("None") }
            } else {
                nudges.forEachIndexed { i, n ->
                    item(key = "n$i") { NudgeRow(timeLabel(n.ts), n.level) }
                }
            }
            item { SectionLabel(Icons.Rounded.GraphicEq, "Busiest periods", Palette.Teal) }
            if (busiest.isEmpty()) {
                item { MutedText("No snoring") }
            } else {
                busiest.forEachIndexed { i, b ->
                    item(key = "b$i") {
                        ListRow(
                            Icons.Rounded.GraphicEq, Palette.Teal,
                            "${timeLabel(s.startedAt + b.startOffsetMs)}-${timeLabel(s.startedAt + b.endOffsetMs)}",
                            "${b.events} snores",
                        )
                    }
                }
            }
        }
    }
}

private fun endReasonIcon(reason: String?): ImageVector = when (reason) {
    "user", "stopped" -> Icons.Rounded.Stop
    "stopAt" -> Icons.Rounded.Alarm
    "maxSession" -> Icons.Rounded.Timer
    "lowBattery" -> Icons.Rounded.BatteryAlert
    "interrupted" -> Icons.Rounded.Warning
    else -> Icons.Rounded.PlayArrow
}

@Composable
private fun NudgeRow(time: String, level: Int) =
    ListRow(Icons.Rounded.Vibration, ChartColors.nudge(level), time, "Level $level")

@Composable
private fun ListRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth(0.78f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(icon, tint, size = 16.dp)
        Text(left, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        Text(right, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun TimelineCard(
    start: Long,
    end: Long,
    events: List<SnoreEventEntity>,
    nudges: List<Nudge>,
    delayMs: Long,
) {
    val zone = remember { ZoneId.systemDefault() }
    val duration = (end - start).coerceAtLeast(60_000L)
    val buckets = remember(start, end, events) { Aggregations.buckets(start, end, events.map { it.ts }) }
    val maxEvents = (buckets.maxOfOrNull { it.events } ?: 0).coerceAtLeast(1)
    val ticks = remember(start, end) { Aggregations.hourTicks(start, end, zone) }
    val labelEvery = if (ticks.size > 6) 2 else 1
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = ChartColors.Label)

    SurfaceCard(fraction = 0.9f) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(116.dp)) {
                val w = size.width
                val markerH = 14.dp.toPx()
                val labelH = 16.dp.toPx()
                val plotTop = markerH
                val plotBottom = size.height - labelH
                val plotH = plotBottom - plotTop

                if (delayMs > 0L) {
                    val dw = (delayMs.toFloat() / duration * w).coerceAtMost(w)
                    drawRoundRect(
                        ChartColors.DelayShade, Offset(0f, plotTop), Size(dw, plotH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
                // Subtle horizontal grid.
                for (f in listOf(0.5f, 1f)) {
                    val y = plotBottom - f * plotH
                    drawRect(ChartColors.Grid, Offset(0f, y), Size(w, 1.dp.toPx()))
                }
                // Hour grid lines, ticks and labels.
                ticks.forEachIndexed { i, off ->
                    val x = off.toFloat() / duration * w
                    drawRect(ChartColors.Grid, Offset(x, plotTop), Size(1.dp.toPx(), plotH))
                    if (i % labelEvery == 0) {
                        val hour = Instant.ofEpochMilli(start + off).atZone(zone).hour
                        val m = measurer.measure(String.format(Locale.US, "%02d", hour), labelStyle)
                        val tx = (x - m.size.width / 2f).coerceIn(0f, w - m.size.width)
                        drawText(m, topLeft = Offset(tx, plotBottom + 4.dp.toPx()))
                    }
                }
                // Rounded bars: one per 10-minute bucket.
                for (b in buckets) {
                    if (b.events == 0) continue
                    val x0 = b.startOffsetMs.toFloat() / duration * w
                    val x1 = (b.endOffsetMs.coerceAtMost(duration)).toFloat() / duration * w
                    val bw = ((x1 - x0) * 0.78f).coerceAtLeast(3f)
                    val h = (b.events.toFloat() / maxEvents * plotH).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        ChartColors.Snoring,
                        Offset(x0 + (x1 - x0 - bw) / 2f, plotBottom - h),
                        Size(bw, h),
                        cornerRadius = CornerRadius(minOf(bw / 2f, 3.dp.toPx())),
                    )
                }
                drawRect(ChartColors.Axis, Offset(0f, plotBottom), Size(w, 1.dp.toPx()))
                // Amber nudge markers along the top strip.
                for (n in nudges) {
                    val x = ((n.ts - start).toFloat() / duration * w).coerceIn(4.dp.toPx(), w - 4.dp.toPx())
                    drawCircle(ChartColors.nudge(n.level), radius = 3.dp.toPx(), center = Offset(x, markerH / 2f))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legend(Palette.Teal, "snores")
                Legend(Palette.Amber, "nudges")
                if (delayMs > 0L) Legend(ChartColors.DelayShade, "delay")
            }
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(top = 1.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = ChartColors.Label)
    }
}
