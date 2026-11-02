package ro.goldenlobster.snorenudge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import ro.goldenlobster.snorenudge.service.MonitorState
import ro.goldenlobster.snorenudge.ui.history.HistoryScreen
import ro.goldenlobster.snorenudge.ui.history.NightDetailScreen
import ro.goldenlobster.snorenudge.ui.settings.SettingPickerScreen
import ro.goldenlobster.snorenudge.ui.settings.SettingsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.material3.AppScaffold
import ro.goldenlobster.snorenudge.service.SnoreMonitorService
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import ro.goldenlobster.snorenudge.ui.HomeScreen
import ro.goldenlobster.snorenudge.ui.LiveTestScreen
import ro.goldenlobster.snorenudge.ui.SetupScreen
import ro.goldenlobster.snorenudge.ui.theme.SnoreNudgeTheme

class MainActivity : ComponentActivity() {
    /** Action requested by the tile; consumed in onResume (the mic FGS may only start while visible). */
    private var pendingTileAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingTileAction = tileActionOf(intent)
        setContent { SnoreNudgeTheme { AppScaffold { Root() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTileAction = tileActionOf(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) handleTileAction()
    }

    override fun onResume() {
        super.onResume()
        handleTileAction()
    }

    private fun handleTileAction() {
        val action = pendingTileAction ?: return
        if (!hasRequiredPermissions(this)) return // setup screen is shown; keep pending until granted
        pendingTileAction = null
        val running = MonitorState.state.value.running
        when (action) {
            ACTION_START_FROM_TILE ->
                if (!running) ContextCompat.startForegroundService(this, Intent(this, SnoreMonitorService::class.java))
            ACTION_STOP_FROM_TILE ->
                if (running) stopService(Intent(this, SnoreMonitorService::class.java))
        }
    }

    private fun tileActionOf(intent: Intent?): String? {
        val fromAction = intent?.action?.takeIf { it == ACTION_START_FROM_TILE || it == ACTION_STOP_FROM_TILE }
        return fromAction ?: intent?.getStringExtra(EXTRA_TILE_ACTION)
            ?.takeIf { it == ACTION_START_FROM_TILE || it == ACTION_STOP_FROM_TILE }
    }

    @Composable
    private fun Root() {
        // Bumped on every resume so permission/battery status is re-read after returning from system screens.
        var tick by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            tick++
            onPauseOrDispose { }
        }
        @Suppress("UNUSED_EXPRESSION") tick

        val ctx = this
        val permsOk = hasRequiredPermissions(ctx)
        if (!permsOk) {
            SetupScreen(
                micGranted = isGranted(ctx, Manifest.permission.RECORD_AUDIO),
                notifGranted = notificationsGranted(ctx),
                batteryExempt = isBatteryExempt(ctx),
                onPermissionsResult = { tick++ },
            )
        } else {
            val nav = rememberSwipeDismissableNavController()
            // Permissions may have been granted just now: run a tile action that was waiting.
            LaunchedEffect(Unit) { handleTileAction() }
            val start = {
                ContextCompat.startForegroundService(ctx, Intent(ctx, SnoreMonitorService::class.java))
            }
            SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        batteryExempt = isBatteryExempt(ctx),
                        onStart = start,
                        onStop = { ctx.stopService(Intent(ctx, SnoreMonitorService::class.java)) },
                        onLiveTest = { nav.navigate("live") },
                        onHistory = { nav.navigate("history") },
                        onSettings = { nav.navigate("settings") },
                        onLastNight = { id -> nav.navigate("night/$id") },
                    )
                }
                composable("live") { LiveTestScreen(onStart = start) }
                composable("history") { HistoryScreen(onNight = { id -> nav.navigate("night/$id") }) }
                composable("night/{id}") { entry ->
                    NightDetailScreen(sessionId = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L)
                }
                composable("settings") { SettingsScreen(onPick = { key -> nav.navigate("picker/$key") }) }
                composable("picker/{key}") { entry ->
                    SettingPickerScreen(
                        key = entry.arguments?.getString("key").orEmpty(),
                        onDone = { nav.popBackStack() },
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_START_FROM_TILE = "ro.goldenlobster.snorenudge.action.START_FROM_TILE"
        const val ACTION_STOP_FROM_TILE = "ro.goldenlobster.snorenudge.action.STOP_FROM_TILE"
        /** Tile LaunchAction cannot set an intent action, so it passes the action string as this extra. */
        const val EXTRA_TILE_ACTION = "tile_action"

        fun isGranted(ctx: Context, perm: String) =
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

        fun notificationsGranted(ctx: Context) =
            Build.VERSION.SDK_INT < 33 || isGranted(ctx, Manifest.permission.POST_NOTIFICATIONS)

        fun hasRequiredPermissions(ctx: Context) =
            isGranted(ctx, Manifest.permission.RECORD_AUDIO) && notificationsGranted(ctx)

        fun isBatteryExempt(ctx: Context): Boolean =
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
    }
}
