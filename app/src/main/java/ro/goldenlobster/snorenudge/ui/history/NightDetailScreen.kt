package ro.goldenlobster.snorenudge.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import ro.goldenlobster.snorenudge.ui.theme.ChartColors
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
                item { Text("Night not found", style = MaterialTheme.typography.bodyMedium) }
                return@ScalingLazyColumn
            }
            val end = s.endedAt ?: s.startedAt
            item { Text(dateLabel(s.startedAt) + if (s.testMode) "  TEST" else "", style = MaterialTheme.typography.titleSmall) }
            item {
                Text(
                    "${timeLabel(s.startedAt)} - ${timeLabel(end)}, ${durationLabel(end - s.startedAt)} h",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    endReasonLabel(s.endReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val l1 = nudges.count { it.level == 1 }
            val l2 = nudges.count { it.level == 2 }
            val l3 = nudges.count { it.level >= 3 }
            item { StatRow("Snore events", s.snoreEvents.toString()) }
            item { StatRow("Snoring minutes", s.snoringMinutes.toString()) }
            item { StatRow("Nudges", s.nudges.toString()) }
            item { StatRow("L1 / L2 / L3", "$l1 / $l2 / $l3") }
            item { StatRow("Noise floor", String.format(Locale.US, "%.0f dBFS", s.avgNoiseFloorDb)) }
            item { StatRow("Gate open", String.format(Locale.US, "%.0f%%", s.gateOpenRatio * 100f)) }

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

            item { SectionTitle("Nudges") }
            if (nudges.isEmpty()) {
                item { Muted("None") }
            } else {
                nudges.forEachIndexed { i, n ->
                    item(key = "n$i") { StatRow(timeLabel(n.ts), "Level ${n.level}") }
                }
            }
            item { SectionTitle("Busiest periods") }
            if (busiest.isEmpty()) {
                item { Muted("No snoring") }
            } else {
                busiest.forEachIndexed { i, b ->
                    item(key = "b$i") {
                        StatRow(
                            "${timeLabel(s.startedAt + b.startOffsetMs)}-${timeLabel(s.startedAt + b.endOffsetMs)}",
                            "${b.events} snores",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Column(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .clip(RoundedCornerShape(20.dp))
            .background(ChartColors.Card)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val w = size.width
            val markerH = 14.dp.toPx()
            val labelH = 16.dp.toPx()
            val plotTop = markerH
            val plotBottom = size.height - labelH
            val plotH = plotBottom - plotTop

            if (delayMs > 0L) {
                val dw = (delayMs.toFloat() / duration * w).coerceAtMost(w)
                drawRect(ChartColors.DelayShade, Offset(0f, plotTop), Size(dw, plotH))
            }
            // Bars: one per 10-minute bucket.
            for (b in buckets) {
                if (b.events == 0) continue
                val x0 = b.startOffsetMs.toFloat() / duration * w
                val x1 = (b.endOffsetMs.coerceAtMost(duration)).toFloat() / duration * w
                val bw = ((x1 - x0) * 0.8f).coerceAtLeast(2f)
                val h = (b.events.toFloat() / maxEvents * plotH).coerceAtLeast(3f)
                drawRect(ChartColors.Snoring, Offset(x0 + (x1 - x0 - bw) / 2f, plotBottom - h), Size(bw, h))
            }
            drawRect(ChartColors.Axis, Offset(0f, plotBottom), Size(w, 1.dp.toPx()))
            // Hour ticks and labels.
            ticks.forEachIndexed { i, off ->
                val x = off.toFloat() / duration * w
                drawRect(ChartColors.Axis, Offset(x, plotBottom), Size(1.dp.toPx(), 4.dp.toPx()))
                if (i % labelEvery == 0) {
                    val hour = Instant.ofEpochMilli(start + off).atZone(zone).hour
                    val text = String.format(Locale.US, "%02d", hour)
                    val m = measurer.measure(text, labelStyle)
                    val tx = (x - m.size.width / 2f).coerceIn(0f, w - m.size.width)
                    drawText(m, topLeft = Offset(tx, plotBottom + 5.dp.toPx()))
                }
            }
            // Nudge markers along the top strip.
            for (n in nudges) {
                val x = ((n.ts - start).toFloat() / duration * w).coerceIn(4.dp.toPx(), w - 4.dp.toPx())
                drawCircle(ChartColors.nudge(n.level), radius = 3.5.dp.toPx(), center = Offset(x, markerH / 2f))
            }
        }
        Text(
            "Bars: snores per 10 min. Dots: nudges (light to dark = L1 to L3). Shaded: start delay.",
            style = MaterialTheme.typography.bodySmall,
            color = ChartColors.Label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
