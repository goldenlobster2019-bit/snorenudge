package ro.goldenlobster.snorenudge.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import ro.goldenlobster.snorenudge.ui.components.GlyphIcon
import ro.goldenlobster.snorenudge.ui.components.MutedText
import ro.goldenlobster.snorenudge.ui.components.ScreenHeader
import ro.goldenlobster.snorenudge.ui.components.rememberHeaderListState
import ro.goldenlobster.snorenudge.ui.theme.Palette

@Composable
fun SetupScreen(
    micGranted: Boolean,
    notifGranted: Boolean,
    batteryExempt: Boolean,
    onPermissionsResult: () -> Unit,
) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionsResult()
    }
    SetupContent(
        micGranted = micGranted,
        notifGranted = notifGranted,
        batteryExempt = batteryExempt,
        onGrant = {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        },
        onBatteryExemption = { requestBatteryExemption(ctx) },
    )
}

/** Stateless Setup content: plain data in, callbacks out. Used by [SetupScreen] and the screenshot tests. */
@Composable
internal fun SetupContent(
    micGranted: Boolean,
    notifGranted: Boolean,
    batteryExempt: Boolean,
    onGrant: () -> Unit,
    onBatteryExemption: () -> Unit,
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
            item { ScreenHeader(Icons.Rounded.Bedtime, "Setup") }
            item {
                MutedText("SnoreNudge listens through the watch microphone at night. Audio never leaves the watch.")
            }
            item { PermissionRow(Icons.Rounded.Mic, "Microphone", micGranted) }
            item { PermissionRow(Icons.Rounded.NotificationsActive, "Notifications", notifGranted) }
            item {
                Button(
                    onClick = onGrant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    icon = { GlyphIcon(Icons.Rounded.CheckCircle, Palette.Background, size = 22.dp) },
                    label = { Text("Grant permissions") },
                )
            }
            item { PermissionRow(Icons.Rounded.BatteryFull, "Battery", batteryExempt) }
            item {
                FilledTonalButton(
                    onClick = onBatteryExemption,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    icon = { GlyphIcon(Icons.Rounded.BatteryFull, Palette.Teal, size = 22.dp) },
                    label = { Text("Battery exemption") },
                )
            }
            item { MutedText("Set SnoreNudge to Unrestricted in battery settings") }
        }
    }
}

/** Permission row: icon, name and a check / warning status icon. */
@Composable
internal fun PermissionRow(icon: ImageVector, label: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(icon, Palette.TextSecondary, size = 18.dp)
        Text(label, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 2)
        if (ok) {
            GlyphIcon(Icons.Rounded.CheckCircle, Palette.Teal, size = 20.dp)
        } else {
            GlyphIcon(Icons.Rounded.Warning, Palette.Amber, size = 20.dp)
        }
    }
}

private fun requestBatteryExemption(ctx: android.content.Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        )
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            // Nothing else we can do; the on-screen note tells the user where to look.
        }
    }
}
