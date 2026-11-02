package ro.goldenlobster.snorenudge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import ro.goldenlobster.snorenudge.data.AppSettings
import ro.goldenlobster.snorenudge.data.Sensitivity
import ro.goldenlobster.snorenudge.data.SettingsRepository
import ro.goldenlobster.snorenudge.data.VibrationStrength
import ro.goldenlobster.snorenudge.haptics.VibrationController

private class SettingSpec(val key: String, val title: String)

private val SPECS = listOf(
    SettingSpec("sensitivity", "Sensitivity"),
    SettingSpec("events", "Events to trigger"),
    SettingSpec("cooldown", "Cooldown"),
    SettingSpec("delay", "Start delay"),
    SettingSpec("stop", "Auto-stop at"),
    SettingSpec("vibration", "Vibration strength"),
    SettingSpec("max", "Max nudges per night"),
)

private fun valueLabel(key: String, s: AppSettings): String = when (key) {
    "sensitivity" -> s.sensitivity.label
    "events" -> "${s.eventsToTrigger} snores"
    "cooldown" -> "${s.cooldownSec} s"
    "delay" -> if (s.startDelayMin == 0) "None" else "${s.startDelayMin} min"
    "stop" -> if (s.autoStopMinutes < 0) "Off" else AppSettings.formatStop(s.autoStopMinutes)
    "vibration" -> s.strength.label
    else -> "${s.maxNudges}"
}

@Composable
fun SettingsScreen(onPick: (String) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val repo = remember { SettingsRepository(ctx) }
    val settings by repo.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Settings", style = MaterialTheme.typography.titleSmall) }
            item {
                Text(
                    "Changes apply on next start",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SPECS.forEach { spec ->
                item(key = spec.key) {
                    FilledTonalButton(
                        onClick = { onPick(spec.key) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text(spec.title) },
                        secondaryLabel = { Text(valueLabel(spec.key, settings)) },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = { VibrationController(ctx, settings.strength).vibrate(2) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    label = { Text("Test vibration") },
                    secondaryLabel = { Text("Level 2, ${settings.strength.label}") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { scope.launch { repo.reset() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    label = { Text("Reset to defaults") },
                )
            }
        }
    }
}

private class Option(val label: String, val selected: Boolean, val apply: (AppSettings) -> AppSettings)

private fun optionsFor(key: String, s: AppSettings): Pair<String, List<Option>> = when (key) {
    "sensitivity" -> "Sensitivity" to Sensitivity.entries.map { o ->
        Option(o.label, s.sensitivity == o) { it.copy(sensitivity = o) }
    }
    "events" -> "Events to trigger" to AppSettings.EVENTS.map { v ->
        Option("$v snores", s.eventsToTrigger == v) { it.copy(eventsToTrigger = v) }
    }
    "cooldown" -> "Cooldown" to AppSettings.COOLDOWNS.map { v ->
        Option("$v s", s.cooldownSec == v) { it.copy(cooldownSec = v) }
    }
    "delay" -> "Start delay" to AppSettings.START_DELAYS.map { v ->
        Option(if (v == 0) "None" else "$v min", s.startDelayMin == v) { it.copy(startDelayMin = v) }
    }
    "stop" -> "Auto-stop at" to AppSettings.AUTO_STOPS.map { v ->
        Option(if (v < 0) "Off" else AppSettings.formatStop(v), s.autoStopMinutes == v) { it.copy(autoStopMinutes = v) }
    }
    "vibration" -> "Vibration strength" to VibrationStrength.entries.map { o ->
        Option(o.label, s.strength == o) { it.copy(strength = o) }
    }
    else -> "Max nudges" to AppSettings.MAX_NUDGES.map { v ->
        Option("$v", s.maxNudges == v) { it.copy(maxNudges = v) }
    }
}

@Composable
fun SettingPickerScreen(key: String, onDone: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val repo = remember { SettingsRepository(ctx) }
    val settings by repo.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val (title, options) = optionsFor(key, settings)
    val selectedIndex = options.indexOfFirst { it.selected }
    LaunchedEffect(key, selectedIndex) { if (selectedIndex > 0) listState.scrollToItem(selectedIndex + 1) }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center) }
            options.forEachIndexed { i, o ->
                item(key = "$key-$i") {
                    RadioButton(
                        selected = o.selected,
                        onSelect = { scope.launch { repo.update(o.apply); onDone() } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        label = { Text(o.label) },
                    )
                }
            }
        }
    }
}
