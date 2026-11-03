package ro.goldenlobster.snorenudge.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import ro.goldenlobster.snorenudge.MainActivity
import ro.goldenlobster.snorenudge.R
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
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RES_VERSION)
                    .addIdToImageMapping(ID_MOON, drawableRes(R.drawable.ic_tile_moon))
                    .addIdToImageMapping(ID_PLAY, drawableRes(R.drawable.ic_tile_play))
                    .addIdToImageMapping(ID_STOP, drawableRes(R.drawable.ic_tile_stop))
                    .build(),
            )
            "snore-tile-res"
        }

    private fun drawableRes(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build(),
            )
            .build()

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
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(header())
            .addContent(spacer(6f))
            .addContent(text(lines[0], 18f, TEXT_PRIMARY, bold = true))
            .addContent(spacer(2f))
            .addContent(text(lines[1], 12f, TEXT_MUTED, bold = false))
            .addContent(spacer(12f))
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

    /** Moon icon with the app name. */
    private fun header(): LayoutElementBuilders.Row =
        LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(icon(ID_MOON, 16f))
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).build())
            .addContent(text("SnoreNudge", 12f, TEXT_MUTED, bold = false))
            .build()

    private fun icon(id: String, size: Float): LayoutElementBuilders.Image =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(id)
            .setWidth(dp(size))
            .setHeight(dp(size))
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
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(25f)).build())
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
            .setHeight(dp(50f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(mods)
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(icon(if (stop) ID_STOP else ID_PLAY, 22f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(text(label, 18f, if (stop) BTN_STOP_TEXT else BTN_START_TEXT, bold = true))
                    .build(),
            )
            .build()
    }

    private companion object {
        const val RES_VERSION = "2"
        const val ID_MOON = "moon"
        const val ID_PLAY = "play"
        const val ID_STOP = "stop_icon"
        val TEXT_PRIMARY = 0xFFE6EAF0.toInt()
        val TEXT_MUTED = 0xFF9AA4B2.toInt()
        val BTN_START = 0xFF4FD1C5.toInt()
        val BTN_START_TEXT = 0xFF06201D.toInt()
        val BTN_STOP = 0xFF3A1F1F.toInt()
        val BTN_STOP_TEXT = 0xFFE8908A.toInt()
    }
}
