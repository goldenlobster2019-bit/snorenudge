package ro.goldenlobster.snorenudge.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.data.AppDatabase
import ro.goldenlobster.snorenudge.data.NightSession
import ro.goldenlobster.snorenudge.ui.theme.ChartColors
import java.util.Locale

private const val NIGHTS = 30

@Composable
fun HistoryScreen(onNight: (Long) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val flow = remember { AppDatabase.get(ctx).sessions().observeFinished(NIGHTS) }
    val sessions by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberScalingLazyListState()
    // Test-mode runs are listed but kept out of the averages and the chart.
    val real = sessions.filter { !it.testMode }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Last 30 nights", style = MaterialTheme.typography.titleSmall) }
            if (sessions.isEmpty()) {
                item {
                    Text(
                        "No nights recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { SummaryCard(real) }
                sessions.forEach { s ->
                    item(key = s.id) { NightButton(s) { onNight(s.id) } }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(nights: List<NightSession>) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .clip(RoundedCornerShape(20.dp))
            .background(ChartColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val n = nights.size
        SummaryLine("Nights tracked", n.toString())
        SummaryLine("Avg snoring", if (n == 0) "--" else String.format(Locale.US, "%.0f min", nights.sumOf { it.snoringMinutes } / n.toDouble()))
        SummaryLine("Avg nudges", if (n == 0) "--" else String.format(Locale.US, "%.1f", nights.sumOf { it.nudges } / n.toDouble()))
        if (n > 0) {
            val chrono = nights.sortedBy { it.startedAt }
            NightsBarChart(
                values = chrono.map { it.snoringMinutes },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shortDateLabel(chrono.first().startedAt), style = MaterialTheme.typography.bodySmall, color = ChartColors.Label)
                Text(shortDateLabel(chrono.last().startedAt), style = MaterialTheme.typography.bodySmall, color = ChartColors.Label)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** One thin bar per night (snoring minutes), oldest left, newest right, in a fixed 30-slot grid. */
@Composable
private fun NightsBarChart(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(44.dp)) {
        val slots = NIGHTS
        val slotW = size.width / slots
        val barW = (slotW * 0.62f).coerceAtLeast(2f)
        val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val base = size.height
        val offset = slots - values.size
        drawRect(ChartColors.Axis, Offset(0f, base - 1f), Size(size.width, 1f))
        values.forEachIndexed { i, v ->
            val h = if (v <= 0) 2f else (v.toFloat() / maxV * (size.height - 4f)).coerceAtLeast(3f)
            val x = (offset + i) * slotW + (slotW - barW) / 2f
            drawRect(
                if (v <= 0) ChartColors.SnoringDim else ChartColors.Snoring,
                Offset(x, base - 1f - h), Size(barW, h),
            )
        }
    }
}

@Composable
private fun NightButton(s: NightSession, onClick: () -> Unit) {
    val duration = durationLabel((s.endedAt ?: s.startedAt) - s.startedAt)
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        label = { Text(if (s.testMode) "${dateLabel(s.startedAt)}  TEST" else dateLabel(s.startedAt)) },
        secondaryLabel = { Text("$duration h, ${s.snoringMinutes} min snoring, ${s.nudges} nudges", maxLines = 2) },
    )
}
