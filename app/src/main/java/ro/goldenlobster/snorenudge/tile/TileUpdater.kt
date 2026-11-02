package ro.goldenlobster.snorenudge.tile

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService

object TileUpdater {
    /** Asks the system to re-render the tile; harmless if the tile is not installed. */
    fun request(context: Context) {
        try {
            TileService.getUpdater(context.applicationContext).requestUpdate(SnoreTileService::class.java)
        } catch (e: Throwable) {
            Log.w("SnoreNudge", "Tile update request failed", e)
        }
    }
}
