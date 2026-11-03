package ro.goldenlobster.snorenudge.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Mirrors the night CSV logs and a database snapshot to the app's external files dir
 * (Android/data/ro.goldenlobster.snorenudge/files/export), so they can be pulled with
 * plain `adb pull` without run-as. App-scoped: other apps cannot read it, and it is
 * removed when the app is uninstalled.
 */
object LogExporter {
    private const val TAG = "SnoreNudge"
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "export").also { it.isDaemon = true } }

    fun exportAsync(context: Context, delayMs: Long = 0L) {
        val app = context.applicationContext
        executor.execute {
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                export(app)
            } catch (e: Throwable) {
                Log.w(TAG, "Log export failed", e)
            }
        }
    }

    private fun export(ctx: Context) {
        val base = ctx.getExternalFilesDir(null) ?: return
        val out = File(base, "export").apply { mkdirs() }
        val nightsOut = File(out, "nights").apply { mkdirs() }
        val nights = File(ctx.filesDir, "nights").listFiles { f -> f.name.endsWith(".csv") }.orEmpty()
        val keep = nights.map { it.name }.toSet()
        nightsOut.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.delete() }
        for (f in nights) {
            val dst = File(nightsOut, f.name)
            if (!dst.exists() || dst.length() != f.length() || dst.lastModified() < f.lastModified()) {
                f.copyTo(dst, overwrite = true)
            }
        }
        val db = ctx.getDatabasePath("snorenudge.db")
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(db.path + suffix)
            val dst = File(out, "snorenudge.db$suffix")
            if (src.exists()) src.copyTo(dst, overwrite = true) else dst.delete()
        }
        Log.i(TAG, "Exported ${nights.size} night logs + db to ${out.absolutePath}")
    }
}
