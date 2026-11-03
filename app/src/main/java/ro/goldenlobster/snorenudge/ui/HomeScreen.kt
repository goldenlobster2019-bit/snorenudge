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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
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
import ro.goldenlobster.snorenudge.service.MonitorSnapshot
import ro.goldenlobster.snorenudge.service.MonitorState
import ro.goldenlobster.snorenudge.ui.components.CenteredTonalButton
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
    HomeContent(
        snap = snap,
        now = now,
        battery = battery,
        batteryExempt = batteryExempt,
        lastNight = lastNight,
        onStart = onStart,
        onStop = onStop,
        onLiveTest = onLiveTest,
        onHistory = onHistory,
        onSettings = onSettings,
        onLastNight = onLastNight,
        onTestModeOff = { MonitorState.setTestMode(false) },
    )
}

/** Stateless Home content: plain data in, callbacks out. Used by [HomeScreen] and by the screenshot tests. */
@Composable
internal fun HomeContent(
    snap: MonitorSnapshot,
    now: Long,
    battery: BatteryInfo,
    batteryExempt: Boolean,
    lastNight: NightSession?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLiveTest: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLastNight: (Long) -> Unit,
    onTestModeOff: () -> Unit,
    /** Initially centered list item; defaults to the hero (Start button / running ring). */
    initialCenterItemIndex: Int? = null,
) {
    // Main element (running ring / idle Start button) sits at a fixed index in each mode.
    val heroIndex = if (snap.running) RUNNING_HERO_INDEX else IDLE_START_INDEX
    key(snap.running) {
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = initialCenterItemIndex ?: heroIndex,
        initialCenterItemScrollOffset = 0,
    )

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            autoCentering = AutoCenteringParams(itemIndex = heroIndex, itemOffset = 0),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.6f,
                edgeAlpha = 0.3f,
                minTransitionArea = 0.4f,
                maxTransitionArea = 0.75f,
            ),
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
                    onTestModeOff = onTestModeOff,
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
}

/** Idle list order: title = 0, Start = 1, everything optional comes after. */
private const val IDLE_START_INDEX = 1
/** Running list order: ring hero = 0. */
private const val RUNNING_HERO_INDEX = 0

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.idleItems(
    testMode: Boolean,
    battery: BatteryInfo,
    batteryExempt: Boolean,
    lastNight: NightSession?,
    onStart: () -> Unit,
    onTestModeOff: () -> Unit,
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
            CenteredTonalButton(
                onClick = onTestModeOff,
                icon = Icons.Rounded.Science,
                iconTint = Palette.Amber,
                label = "Test mode on",
                secondaryLabel = "Tap to turn off",
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Palette.AmberDeep,
                    contentColor = Palette.Amber,
                    secondaryContentColor = Palette.Amber.copy(alpha = 0.75f),
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true, borderColor = Palette.Amber.copy(alpha = 0.5f)),
            )
        }
    }
    item { BatteryRow(battery) }
    lastNight?.let { night ->
        item {
            CenteredTonalButton(
                onClick = { onLastNight(night.id) },
                icon = Icons.Rounded.Bedtime,
                iconTint = Palette.Teal,
                label = "Last night",
                secondaryLabel = "${night.snoringMinutes} min snoring, ${night.nudges} nudges",
            )
        }
    }
    item {
        CenteredTonalButton(
            onClick = onHistory,
            icon = Icons.Rounded.History,
            iconTint = Palette.Teal,
            label = "History",
        )
    }
    item {
        CenteredTonalButton(
            onClick = onLiveTest,
            icon = Icons.Rounded.GraphicEq,
            iconTint = Palette.Teal,
            label = "Live test",
        )
    }
    item {
        CenteredTonalButton(
            onClick = onSettings,
            icon = Icons.Rounded.Settings,
            iconTint = Palette.Teal,
            label = "Settings",
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
    // Night-friendly tonal button: dark teal fill, 2 dp teal ring, teal icon and label (no bright disc).
    Box(
        modifier = Modifier
            .size(124.dp)
            .border(1.5.dp, Palette.Teal.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(StartFill)
                .border(2.dp, Palette.Teal, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Start monitoring", onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlyphIcon(Icons.Rounded.PlayArrow, Palette.Teal, size = 44.dp)
                Text("Start", style = MaterialTheme.typography.titleMedium, color = Palette.Teal)
            }
        }
    }
}

private val StartFill = androidx.compose.ui.graphics.Color(0xFF12302E)

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
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                GlyphIcon(Icons.Rounded.GraphicEq, Palette.Teal, size = 18.dp)
                Text(snap.snoreEventCount.toString(), style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                Spacer(Modifier.width(8.dp))
                GlyphIcon(Icons.Rounded.Vibration, Palette.Amber, size = 18.dp)
                Text(snap.nudgeCount.toString(), style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            }
            if (snap.startDelayRemainingMs > 0L) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GlyphIcon(Icons.Rounded.Timer, Palette.TextSecondary, size = 12.dp)
                    Text(
                        "Nudges in " + formatMinSec(snap.startDelayRemainingMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
            }
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
