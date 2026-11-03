package ro.goldenlobster.snorenudge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ChildButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import ro.goldenlobster.snorenudge.data.AppSettings
import ro.goldenlobster.snorenudge.data.Sensitivity
import ro.goldenlobster.snorenudge.data.SettingsRepository
import ro.goldenlobster.snorenudge.data.VibrationStrength
import ro.goldenlobster.snorenudge.haptics.VibrationController
import ro.goldenlobster.snorenudge.ui.components.GlyphIcon
import ro.goldenlobster.snorenudge.ui.components.MutedText
import ro.goldenlobster.snorenudge.ui.components.ScreenHeader
import ro.goldenlobster.snorenudge.ui.components.rememberHeaderListState
import ro.goldenlobster.snorenudge.ui.theme.Palette

private class SettingSpec(val key: String, val title: String, val icon: ImageVector)

private val SPECS = listOf(
    SettingSpec("sensitivity", "Sensitivity", Icons.Rounded.Tune),
    SettingSpec("events", "Events to trigger", Icons.Rounded.GraphicEq),
    SettingSpec("cooldown", "Cooldown", Icons.Rounded.Timer),
    SettingSpec("delay", "Start delay", Icons.Rounded.HourglassEmpty),
    SettingSpec("stop", "Auto-stop at", Icons.Rounded.Alarm),
    SettingSpec("vibration", "Vibration strength", Icons.Rounded.Vibration),
    SettingSpec("max", "Max nudges per night", Icons.Rounded.NotificationsActive),
)

private fun valueLabel(key: String, s: AppSettings): String = when (key) {
    "sensitivity" -> s.sensitivity.label
    "events" -> "${s.eventsToTrigger} snores"
    "cooldown" -> "${s.cooldownSec} s"
    "delay" -> if (s.startDelayMin == 0) "None" else "${s.startDelayMin} min"
    "stop" -> if (s.autoStopMinutes < 0) "Off" else AppSettings.formatStop(s.autoStopMinutes)
    "vibration" -> s.strength.label
    else -> AppSettings.formatMax(s.maxNudges)
}

@Composable
fun SettingsScreen(onPick: (String) -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val repo = remember { SettingsRepository(ctx) }
    val settings by repo.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    SettingsContent(
        settings = settings,
        onPick = onPick,
        onTestVibration = { VibrationController(ctx, settings.strength).vibrate(2) },
        onReset = { scope.launch { repo.reset() } },
    )
}

/** Stateless Settings content: plain data in, callbacks out. Used by [SettingsScreen] and the screenshot tests. */
@Composable
internal fun SettingsContent(
    settings: AppSettings,
    onPick: (String) -> Unit,
    onTestVibration: () -> Unit,
    onReset: () -> Unit,
) {
    val listState = rememberHeaderListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            autoCentering = null,
        ) {
            item { ScreenHeader(Icons.Rounded.Settings, "Settings") }
            item { MutedText("Changes apply on next start") }
            SPECS.forEach { spec ->
                item(key = spec.key) {
                    FilledTonalButton(
                        onClick = { onPick(spec.key) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        icon = { GlyphIcon(spec.icon, Palette.Teal, size = 22.dp) },
                        label = { Text(spec.title, maxLines = 2) },
                        secondaryLabel = { Text(valueLabel(spec.key, settings)) },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = onTestVibration,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    icon = { GlyphIcon(Icons.Rounded.Vibration, Palette.Amber, size = 22.dp) },
                    label = { Text("Test vibration") },
                    secondaryLabel = { Text("Level 2, ${settings.strength.label}") },
                )
            }
            item {
                ChildButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.childButtonColors(contentColor = Palette.TextSecondary),
                    icon = { GlyphIcon(Icons.Rounded.RestartAlt, Palette.TextSecondary, size = 20.dp) },
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
    else -> "Max nudges per night" to AppSettings.MAX_NUDGES.map { v ->
        Option(AppSettings.formatMax(v), s.maxNudges == v) { it.copy(maxNudges = v) }
    }
}

@Composable
fun SettingPickerScreen(key: String, onDone: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val repo = remember { SettingsRepository(ctx) }
    val settings by repo.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    SettingPickerContent(
        key = key,
        settings = settings,
        onSelect = { apply -> scope.launch { repo.update(apply); onDone() } },
    )
}

/** Stateless picker content; [onSelect] receives the transform to apply to the settings. */
@Composable
internal fun SettingPickerContent(
    key: String,
    settings: AppSettings,
    onSelect: ((AppSettings) -> AppSettings) -> Unit,
) {
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
            item { ScreenHeader(SPECS.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Settings, title) }
            options.forEachIndexed { i, o ->
                item(key = "$key-$i") {
                    RadioButton(
                        selected = o.selected,
                        onSelect = { onSelect(o.apply) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        label = { Text(o.label) },
                    )
                }
            }
        }
    }
}
