package ro.goldenlobster.snorenudge.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import ro.goldenlobster.snorenudge.MainActivity
import ro.goldenlobster.snorenudge.data.AppDatabase
import ro.goldenlobster.snorenudge.data.NightSession
import ro.goldenlobster.snorenudge.service.MonitorState
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Start/Stop tile. Buttons only open MainActivity (microphone FGS must start while the app is visible). */
class SnoreTileService : TileService() {

    private val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "tile").also { it.isDaemon = true } }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            executor.execute {
                try {
                    completer.set(buildTile())
                } catch (e: Throwable) {
                    completer.setException(e)
                }
            }
            "snore-tile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())
            "snore-tile-res"
        }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun buildTile(): TileBuilders.Tile {
        val snap = MonitorState.state.value
        val lastNight: NightSession? = if (snap.running) null else {
            try { AppDatabase.get(applicationContext).sessions().latestFinished() } catch (_: Throwable) { null }
        }
        val lines: List<String> = if (snap.running) {
            val elapsed = (System.currentTimeMillis() - snap.startedAt).coerceAtLeast(0L) / 60_000L
            listOf(
                String.format(Locale.US, "Monitoring %d:%02d", elapsed / 60, elapsed % 60),
                "${snap.snoreEventCount} snores, ${snap.nudgeCount} nudges",
            )
        } else {
            listOf("Not running", lastNightLine(lastNight))
        }

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(lines[0], 18f, TEXT_PRIMARY, bold = true))
            .addContent(spacer(4f))
            .addContent(text(lines[1], 13f, TEXT_MUTED, bold = false))
            .addContent(spacer(14f))
            .addContent(button(if (snap.running) "Stop" else "Start", snap.running))
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(if (snap.running) 5 * 60_000L else 0L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    private fun lastNightLine(s: NightSession?): String =
        if (s == null) "No nights yet"
        else "Last night: ${s.snoringMinutes} min snoring, ${s.nudges} nudges"

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(2)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(color))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL,
                    )
                    .build(),
            )
            .build()

    private fun spacer(h: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    private fun button(label: String, stop: Boolean): LayoutElementBuilders.Box {
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        MainActivity.EXTRA_TILE_ACTION,
                        ActionBuilders.AndroidStringExtra.Builder()
                            .setValue(if (stop) MainActivity.ACTION_STOP_FROM_TILE else MainActivity.ACTION_START_FROM_TILE)
                            .build(),
                    )
                    .build(),
            )
            .build()
        val mods = ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(argb(if (stop) BTN_STOP else BTN_START))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(26f)).build())
                    .build(),
            )
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(if (stop) "stop" else "start")
                    .setOnClick(launch)
                    .build(),
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(150f))
            .setHeight(dp(52f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(mods)
            .addContent(text(label, 20f, if (stop) 0xFFF2CFCF.toInt() else 0xFF07201A.toInt(), bold = true))
            .build()
    }

    private companion object {
        const val RES_VERSION = "1"
        val TEXT_PRIMARY = 0xFFD0D4E2.toInt()
        val TEXT_MUTED = 0xFF9AA0B5.toInt()
        val BTN_START = 0xFF8FC7B8.toInt()
        val BTN_STOP = 0xFF5A2323.toInt()
    }
}
