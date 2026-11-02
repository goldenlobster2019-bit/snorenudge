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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SnoreNudgeTheme { AppScaffold { Root() } } }
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
                    )
                }
                composable("live") { LiveTestScreen(onStart = start) }
            }
        }
    }

    companion object {
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
