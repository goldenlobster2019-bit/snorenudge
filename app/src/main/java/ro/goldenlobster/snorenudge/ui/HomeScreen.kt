package ro.goldenlobster.snorenudge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import ro.goldenlobster.snorenudge.service.MonitorState
import java.util.Locale

@Composable
fun HomeScreen(
    batteryExempt: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLiveTest: () -> Unit,
) {
    val snap by MonitorState.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snap.running) {
        while (snap.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                if (snap.running) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) { Text("Stop", style = MaterialTheme.typography.titleMedium) }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
                }
            }
            if (snap.running) {
                item {
                    Text(
                        formatElapsed(now - snap.startedAt),
                        style = MaterialTheme.typography.numeralMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (snap.testMode) {
                    item {
                        Text(
                            "TEST MODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                item {
                    val remaining = snap.startDelayRemainingMs
                    if (remaining > 0L) {
                        Text(
                            "Nudges active in " + formatMinSec(remaining),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { CounterRow("Snores", snap.snoreEventCount.toString()) }
                item {
                    CounterRow(
                        "Nudges",
                        if (snap.lastNudgeAt > 0L) {
                            "${snap.nudgeCount} (${hhmm(snap.lastNudgeAt)})"
                        } else snap.nudgeCount.toString(),
                    )
                }
                item {
                    val db = snap.lastDb
                    Text(
                        if (db == null) "Level: --" else String.format(Locale.US, "Level: %.1f dBFS", db),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    val src = snap.chosenAudioSource
                    if (src != null) {
                        Text(
                            "$src, ${snap.sampleRate / 1000} kHz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (!batteryExempt) {
                item {
                    Text(
                        "Tip: set SnoreNudge to Unrestricted in battery settings",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = onLiveTest,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Live test") }
            }
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
    }
}

internal fun formatElapsed(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

@Composable
private fun CounterRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(0.8f),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun formatMinSec(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999) / 1000
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

internal fun hhmm(wallMs: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(wallMs))
