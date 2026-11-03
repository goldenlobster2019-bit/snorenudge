package ro.goldenlobster.snorenudge.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.haptics.VibrationController
import ro.goldenlobster.snorenudge.service.MonitorState
import ro.goldenlobster.snorenudge.ui.components.GlyphIcon
import ro.goldenlobster.snorenudge.ui.components.MeterBar
import ro.goldenlobster.snorenudge.ui.components.MutedText
import ro.goldenlobster.snorenudge.ui.components.Pill
import ro.goldenlobster.snorenudge.ui.components.ScreenHeader
import ro.goldenlobster.snorenudge.ui.components.SectionLabel
import ro.goldenlobster.snorenudge.ui.components.SurfaceCard
import ro.goldenlobster.snorenudge.ui.theme.Palette
import java.util.Locale

/** dBFS range mapped onto the level bar. */
private const val DB_MIN = -90f
private const val DB_MAX = -10f

private fun dbFraction(db: Float): Float = ((db - DB_MIN) / (DB_MAX - DB_MIN)).coerceIn(0f, 1f)

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
            item { ScreenHeader(Icons.Rounded.GraphicEq, "Live test") }

            if (!snap.running) {
                item {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        icon = { GlyphIcon(Icons.Rounded.PlayArrow, Palette.Background, size = 24.dp) },
                        label = { Text("Start") },
                    )
                }
                item { MutedText("Start monitoring to see live values") }
            } else {
                item {
                    SurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            MeterHeader(Icons.Rounded.Mic, "Level", snap.lastDb?.let { fmt("%.1f dBFS", it) } ?: "--")
                            MeterBar(
                                fraction = snap.lastDb?.let { dbFraction(it) } ?: 0f,
                                color = Palette.TextSecondary,
                                marker = snap.noiseFloorDb?.let { dbFraction(it) },
                            )
                            Text(
                                "Floor " + (snap.noiseFloorDb?.let { fmt("%.1f dBFS", it) } ?: "--"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextSecondary,
                            )
                        }
                    }
                }
                item {
                    SurfaceCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                GlyphIcon(Icons.Rounded.GraphicEq, Palette.Teal, size = 14.dp)
                                Text("Snoring", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                            }
                            Text(
                                fmt("%.2f", snap.snoreScore),
                                style = MaterialTheme.typography.numeralLarge,
                                color = Palette.Teal,
                            )
                            MeterBar(snap.snoreScore, Palette.Teal)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Competitor", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                                Text(fmt("%.2f", snap.competitorScore), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                            }
                            MeterBar(snap.competitorScore, Palette.TextSecondary.copy(alpha = 0.7f), height = 5.dp)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (snap.positiveFrame) {
                            Pill("POSITIVE", icon = Icons.Rounded.CheckCircle, containerColor = Palette.TealDeep, contentColor = Palette.Teal)
                        } else {
                            Pill("negative")
                        }
                        if (snap.gateOpen) {
                            Pill("Gate open", icon = Icons.Rounded.Sensors, containerColor = Palette.TealDeep, contentColor = Palette.Teal)
                        } else {
                            Pill("Gate closed", icon = Icons.Rounded.Sensors)
                        }
                    }
                }
                item {
                    SurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            DetailRow("Snores", snap.snoreEventCount.toString())
                            DetailRow("Nudges", snap.nudgeCount.toString())
                            DetailRow("Breathing", fmt("%.2f", snap.breathingScore))
                            DetailRow("Speech", fmt("%.2f", snap.speechScore))
                            DetailRow(
                                "Top",
                                snap.topLabel?.let { "$it ${fmt("%.2f", snap.topScore)}" } ?: "--",
                            )
                            DetailRow("Inference", "${snap.lastInferenceMs} ms (${snap.inferenceCount})")
                        }
                    }
                }
                snap.error?.let { err ->
                    item { MutedText(err, color = MaterialTheme.colorScheme.error) }
                }
            }

            item {
                SwitchButton(
                    checked = snap.testMode,
                    onCheckedChange = { MonitorState.setTestMode(it) },
                    enabled = !snap.running,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    icon = { GlyphIcon(Icons.Rounded.Science, if (snap.testMode) Palette.Amber else Palette.TextSecondary, size = 22.dp) },
                    label = { Text("Test mode") },
                    secondaryLabel = {
                        Text(if (snap.running) "Stop to change" else "No delay, 30 s cooldown. Resets after a session", maxLines = 2)
                    },
                )
            }

            item { SectionLabel(Icons.Rounded.Vibration, "Test vibration", Palette.Amber) }
            for (level in 1..3) {
                item {
                    FilledTonalButton(
                        onClick = { vibration.vibrate(level) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        icon = { GlyphIcon(Icons.Rounded.Vibration, Palette.Amber, size = 22.dp) },
                        label = { Text("Level $level") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MeterHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GlyphIcon(icon, Palette.TextSecondary, size = 14.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = Palette.TextPrimary, textAlign = TextAlign.End)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Palette.TextPrimary, maxLines = 1)
    }
}

private fun fmt(f: String, v: Float) = String.format(Locale.US, f, v)
