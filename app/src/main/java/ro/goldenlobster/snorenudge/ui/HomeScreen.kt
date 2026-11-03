package ro.goldenlobster.snorenudge.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import ro.goldenlobster.snorenudge.data.AppDatabase
import ro.goldenlobster.snorenudge.data.NightSession
import ro.goldenlobster.snorenudge.service.MonitorState
import ro.goldenlobster.snorenudge.ui.components.GlyphIcon
import ro.goldenlobster.snorenudge.ui.components.MutedText
import ro.goldenlobster.snorenudge.ui.components.Pill
import ro.goldenlobster.snorenudge.ui.theme.Palette
import java.util.Locale

@Composable
fun HomeScreen(
    batteryExempt: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLiveTest: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLastNight: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val lastNight by remember { AppDatabase.get(ctx.applicationContext).sessions().observeLatestFinished() }
        .collectAsStateWithLifecycle(initialValue = null)
    val snap by MonitorState.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snap.running) {
        while (snap.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val battery = rememberBattery()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (snap.running) {
                runningItems(snap, now, onStop)
            } else {
                idleItems(
                    testMode = snap.testMode,
                    battery = battery,
                    batteryExempt = batteryExempt,
                    lastNight = lastNight,
                    onStart = onStart,
                    onLastNight = onLastNight,
                    onHistory = onHistory,
                    onLiveTest = onLiveTest,
                    onSettings = onSettings,
                )
            }
            snap.error?.let { err ->
                item { MutedText(err, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.idleItems(
    testMode: Boolean,
    battery: BatteryInfo,
    batteryExempt: Boolean,
    lastNight: NightSession?,
    onStart: () -> Unit,
    onLastNight: (Long) -> Unit,
    onHistory: () -> Unit,
    onLiveTest: () -> Unit,
    onSettings: () -> Unit,
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlyphIcon(Icons.Rounded.Bedtime, Palette.Teal, size = 16.dp)
            Text("SnoreNudge", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
    }
    item { StartButton(onStart) }
    if (testMode) {
        item {
            FilledTonalButton(
                onClick = { MonitorState.setTestMode(false) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Palette.AmberDeep,
                    contentColor = Palette.Amber,
                    secondaryContentColor = Palette.Amber.copy(alpha = 0.75f),
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true, borderColor = Palette.Amber.copy(alpha = 0.5f)),
                icon = { GlyphIcon(Icons.Rounded.Science, Palette.Amber, size = 22.dp) },
                label = { Text("Test mode on") },
                secondaryLabel = { Text("Tap to turn off") },
            )
        }
    }
    item { BatteryRow(battery) }
    lastNight?.let { night ->
        item {
            FilledTonalButton(
                onClick = { onLastNight(night.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                icon = { GlyphIcon(Icons.Rounded.Bedtime, Palette.Teal, size = 22.dp) },
                label = { Text("Last night") },
                secondaryLabel = { Text("${night.snoringMinutes} min snoring, ${night.nudges} nudges", maxLines = 2) },
            )
        }
    }
    item {
        FilledTonalButton(
            onClick = onHistory,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            icon = { GlyphIcon(Icons.Rounded.History, Palette.Teal, size = 22.dp) },
            label = { Text("History") },
        )
    }
    item {
        FilledTonalButton(
            onClick = onLiveTest,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            icon = { GlyphIcon(Icons.Rounded.GraphicEq, Palette.Teal, size = 22.dp) },
            label = { Text("Live test") },
        )
    }
    item {
        FilledTonalButton(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            icon = { GlyphIcon(Icons.Rounded.Settings, Palette.Teal, size = 22.dp) },
            label = { Text("Settings") },
        )
    }
    if (!batteryExempt) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(0.82f),
            ) {
                GlyphIcon(Icons.Rounded.Warning, Palette.Amber, size = 14.dp)
                Text(
                    "Set battery to Unrestricted in system settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
            }
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.runningItems(
    snap: ro.goldenlobster.snorenudge.service.MonitorSnapshot,
    now: Long,
    onStop: () -> Unit,
) {
    item { RunningHero(snap, now) }
    item {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CounterLabel(Icons.Rounded.GraphicEq, Palette.Teal, snap.snoreEventCount.toString(), "snores")
            CounterLabel(Icons.Rounded.Vibration, Palette.Amber, snap.nudgeCount.toString(), "nudges")
        }
    }
    if (snap.startDelayRemainingMs > 0L) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlyphIcon(Icons.Rounded.Timer, Palette.TextSecondary, size = 14.dp)
                Text(
                    "Nudges active in " + formatMinSec(snap.startDelayRemainingMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
            }
        }
    }
    if (snap.lastNudgeAt > 0L) {
        item { MutedText("Last nudge ${hhmm(snap.lastNudgeAt)}") }
    }
    item {
        val db = snap.lastDb
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlyphIcon(Icons.Rounded.Mic, Palette.TextSecondary, size = 14.dp)
            Text(
                if (db == null) "--" else String.format(Locale.US, "%.0f dBFS", db),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )
        }
    }
    item {
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Danger),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true, borderColor = Palette.Danger.copy(alpha = 0.6f)),
            icon = { GlyphIcon(Icons.Rounded.Stop, Palette.Danger, size = 22.dp) },
            label = { Text("Stop") },
        )
    }
}

@Composable
private fun StartButton(onStart: () -> Unit) {
    // Outer hairline ring, inner solid teal disc.
    Box(
        modifier = Modifier
            .size(124.dp)
            .border(1.5.dp, Palette.Teal.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(Palette.Teal)
                .clickable(role = Role.Button, onClickLabel = "Start monitoring", onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlyphIcon(Icons.Rounded.PlayArrow, Color0620, size = 44.dp)
                Text("Start", style = MaterialTheme.typography.titleMedium, color = Color0620)
            }
        }
    }
}

private val Color0620 = androidx.compose.ui.graphics.Color(0xFF06201D)

@Composable
private fun RunningHero(snap: ro.goldenlobster.snorenudge.service.MonitorSnapshot, now: Long) {
    val elapsed = (now - snap.startedAt).coerceAtLeast(0L)
    val total = (snap.plannedEndAt - snap.startedAt).takeIf { snap.plannedEndAt > 0L && it > 0L }
    val progress = if (total == null) 0f else (elapsed.toFloat() / total).coerceIn(0f, 1f)
    Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(176.dp),
            startAngle = 270f,
            endAngle = 270f,
            strokeWidth = 8.dp,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = Palette.Teal,
                trackColor = Palette.SurfaceHigh,
            ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (snap.testMode) {
                Pill(
                    "TEST",
                    icon = Icons.Rounded.Science,
                    containerColor = Palette.AmberDeep,
                    contentColor = Palette.Amber,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                formatHoursMinutes(elapsed),
                style = MaterialTheme.typography.numeralLarge,
                color = Palette.TextPrimary,
            )
            if (snap.plannedEndAt > 0L) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GlyphIcon(Icons.Rounded.Schedule, Palette.TextSecondary, size = 12.dp)
                    Text(
                        "until " + hhmm(snap.plannedEndAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        GlyphIcon(icon, tint, size = 18.dp)
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        }
    }
}

@Composable
private fun BatteryRow(battery: BatteryInfo) {
    val pct = battery.percent
    if (pct < 0) return
    val low = pct < 60 && !battery.charging
    val icon = when {
        battery.charging -> Icons.Rounded.BatteryChargingFull
        low -> Icons.Rounded.BatteryAlert
        else -> Icons.Rounded.BatteryFull
    }
    val tint = if (low) Palette.Amber else Palette.TextSecondary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GlyphIcon(icon, tint, size = 16.dp)
            Text("$pct%", style = MaterialTheme.typography.labelMedium, color = tint)
        }
        if (low) {
            Text("Charge before sleep", style = MaterialTheme.typography.labelSmall, color = Palette.Amber)
        }
    }
}

internal data class BatteryInfo(val percent: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): BatteryInfo {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf(BatteryInfo(-1, false)) }
    DisposableEffect(ctx) {
        fun read(i: Intent?) {
            if (i == null) return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            info = BatteryInfo(level * 100 / scale, i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = read(intent)
        }
        // Sticky broadcast: the returned intent carries the current value immediately.
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        read(sticky)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return info
}

internal fun formatElapsed(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

internal fun formatHoursMinutes(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
}

internal fun formatMinSec(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999) / 1000
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

internal fun hhmm(wallMs: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(wallMs))
