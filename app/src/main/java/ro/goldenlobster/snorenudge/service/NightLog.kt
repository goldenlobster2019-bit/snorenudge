package ro.goldenlobster.snorenudge.service

import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Per-night CSV log in app-private storage, written on its own single thread.
 * Header: ts,type,snore,competitor,top,topScore,peakDb,floorDb,level,note
 */
class NightLog(private val dir: File, startWallMs: Long) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "nightlog").also { it.isDaemon = true } }
    private var writer: BufferedWriter? = null
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    private val fileName = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(startWallMs)) + ".csv"

    init {
        executor.execute {
            try {
                dir.mkdirs()
                prune()
                val w = BufferedWriter(FileWriter(File(dir, fileName), true))
                w.write("ts,type,snore,competitor,top,topScore,peakDb,floorDb,level,note\n")
                writer = w
            } catch (e: Throwable) {
                Log.w(TAG, "Night log open failed", e)
            }
        }
    }

    fun row(
        wallMs: Long,
        type: String,
        snore: Float? = null,
        competitor: Float? = null,
        top: String? = null,
        topScore: Float? = null,
        peakDb: Float? = null,
        floorDb: Float? = null,
        level: Int? = null,
        note: String? = null,
    ) {
        try {
            executor.execute {
                val w = writer ?: return@execute
                try {
                    val sb = StringBuilder(96)
                    sb.append(tsFormat.format(Date(wallMs))).append(',').append(type).append(',')
                    sb.append(f(snore)).append(',').append(f(competitor)).append(',')
                    sb.append(csv(top)).append(',').append(f(topScore)).append(',')
                    sb.append(f(peakDb, 1)).append(',').append(f(floorDb, 1)).append(',')
                    sb.append(level?.toString() ?: "").append(',').append(csv(note)).append('\n')
                    w.write(sb.toString())
                } catch (e: Throwable) {
                    Log.w(TAG, "Night log write failed", e)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun flush() {
        try {
            executor.execute { try { writer?.flush() } catch (_: Throwable) {} }
        } catch (_: Exception) {
        }
    }

    /** Flushes pending rows, closes the file and shuts the thread down. */
    fun close() {
        try {
            executor.execute {
                try { writer?.flush(); writer?.close() } catch (_: Throwable) {}
                writer = null
            }
        } catch (_: Exception) {
        }
        executor.shutdown()
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    private fun f(v: Float?, digits: Int = 3): String =
        if (v == null) "" else String.format(Locale.US, "%.${digits}f", v)

    private fun csv(s: String?): String {
        if (s == null) return ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    companion object {
        private const val TAG = "SnoreNudge"
        private const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000
    }
}
