package ro.goldenlobster.snorenudge.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.haptics.VibrationController
import ro.goldenlobster.snorenudge.service.MonitorState
import java.util.Locale

@Composable
fun LiveTestScreen(onStart: () -> Unit) {
    val snap by MonitorState.state.collectAsStateWithLifecycle()
    val ctx: Context = LocalContext.current
    val vibration = remember { VibrationController(ctx.applicationContext) }
    val listState = rememberScalingLazyListState()

    // Forces inference while visible; cleared on leave (also when the activity stops).
    DisposableEffect(Unit) {
        MonitorState.setLiveTestActive(true)
        onDispose { MonitorState.setLiveTestActive(false) }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Live test", style = MaterialTheme.typography.titleSmall) }
            item {
                SwitchButton(
                    checked = snap.testMode,
                    onCheckedChange = { MonitorState.setTestMode(it) },
                    enabled = !snap.running,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Test mode (no delay, 30 s cooldown)", style = MaterialTheme.typography.bodySmall) },
                    secondaryLabel = { Text(if (snap.running) "Stop to change" else "Applies on next start") },
                )
            }

            if (!snap.running) {
                item {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Start") }
                }
                item {
                    Text(
                        "Start monitoring to see live values",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { Row2("Level", snap.lastDb?.let { fmt("%.1f dBFS", it) } ?: "--") }
                item { Row2("Floor", snap.noiseFloorDb?.let { fmt("%.1f dBFS", it) } ?: "--") }
                item {
                    Row2(
                        "Gate",
                        if (snap.gateOpen) "OPEN" else "closed",
                        valueColor = if (snap.gateOpen) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text(
                        "Snoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text(
                        fmt("%.2f", snap.snoreScore),
                        style = MaterialTheme.typography.numeralLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item { ScoreBar(snap.snoreScore) }
                item {
                    Row2(
                        "Competitor", fmt("%.2f", snap.competitorScore),
                    )
                }
                item {
                    Row2(
                        "Frame",
                        if (snap.positiveFrame) "POSITIVE" else "negative",
                        valueColor = if (snap.positiveFrame) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { Row2("Snores", snap.snoreEventCount.toString()) }
                item { Row2("Nudges", snap.nudgeCount.toString()) }
                item { Row2("Breathing", fmt("%.2f", snap.breathingScore)) }
                item { Row2("Speech", fmt("%.2f", snap.speechScore)) }
                item {
                    val label = snap.topLabel
                    Text(
                        if (label == null) "Top: --" else "Top: $label ${fmt("%.2f", snap.topScore)}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                item { Row2("Inference", "${snap.lastInferenceMs} ms") }
                item { Row2("Count", snap.inferenceCount.toString()) }
                snap.error?.let { err ->
                    item {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                Text(
                    "Vibration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (level in 1..3) {
                item {
                    FilledTonalButton(
                        onClick = { vibration.vibrate(level) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Test L$level") }
                }
            }
        }
    }
}

@Composable
private fun Row2(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(0.8f),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}

@Composable
private fun ScoreBar(score: Float) {
    val frac = score.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(frac)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun fmt(f: String, v: Float) = String.format(Locale.US, f, v)
