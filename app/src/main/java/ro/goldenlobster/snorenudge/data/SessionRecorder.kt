package ro.goldenlobster.snorenudge.data

import android.content.Context
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Persists one monitoring session. All database work runs on a private single thread so the audio
 * and inference threads never block. Public methods are safe to call from any thread.
 */
class SessionRecorder(context: Context) {
    private val db = lazy { AppDatabase.get(context) }
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "session-db").also { it.isDaemon = true } }

    // Touched only on the executor thread.
    private var sessionId = -1L
    private var events = 0
    private var nudges = 0
    private val minutes = HashSet<Long>()
    private var floorSum = 0.0
    private var floorSamples = 0
    private var inferences = 0L
    private var inferenceMsTotal = 0L
    private var gateRatio = 0f

    fun start(startedAt: Long, testMode: Boolean, settingsSummary: String) = post {
        val d = db.value
        d.sessions().markOrphansInterrupted()
        d.sessions().deleteStartedBefore(startedAt - RETENTION_MS)
        sessionId = d.sessions().insert(
            NightSession(startedAt = startedAt, endedAt = startedAt, testMode = testMode, settingsSummary = settingsSummary),
        )
    }

    fun onEvent(ts: Long, durationMs: Long, maxScore: Float, peakDb: Float) = post {
        if (sessionId < 0) return@post
        db.value.events().insert(SnoreEventEntity(sessionId = sessionId, ts = ts, durationMs = durationMs, maxScore = maxScore, peakDb = peakDb))
        events++
        minutes.add(Math.floorDiv(ts, 60_000L))
    }

    fun onNudge(ts: Long, level: Int) = post {
        if (sessionId < 0) return@post
        db.value.nudges().insert(Nudge(sessionId = sessionId, ts = ts, level = level))
        nudges++
    }

    /** Called once per minute with stats since the previous call (inference count/ms) and current gate/floor. */
    fun onMinuteSummary(now: Long, gateOpenRatio: Float, floorDb: Float, inferenceCount: Int, inferenceMs: Long) = post {
        if (sessionId < 0) return@post
        gateRatio = gateOpenRatio
        floorSum += floorDb
        floorSamples++
        inferences += inferenceCount
        inferenceMsTotal += inferenceMs
        writeAggregates(now)
    }

    /** Final write; [onDone] runs on the database thread after the row is final. Then the thread shuts down. */
    fun finish(endedAt: Long, reason: String, gateOpenRatio: Float, floorDb: Float?, onDone: () -> Unit = {}) {
        try {
            executor.execute {
                try {
                    if (sessionId >= 0) {
                        gateRatio = gateOpenRatio
                        if (floorDb != null) { floorSum += floorDb; floorSamples++ }
                        writeAggregates(endedAt)
                        db.value.sessions().finish(sessionId, endedAt, reason)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Session finish failed", e)
                }
                try { onDone() } catch (_: Throwable) {}
            }
        } catch (_: Exception) {
        }
        executor.shutdown()
    }

    private fun writeAggregates(now: Long) {
        db.value.sessions().updateAggregates(
            sessionId, now, events, nudges, minutes.size, gateRatio,
            if (floorSamples > 0) (floorSum / floorSamples).toFloat() else 0f,
            if (inferences > 0) inferenceMsTotal.toFloat() / inferences else 0f,
        )
    }

    private inline fun post(crossinline block: () -> Unit) {
        try {
            executor.execute {
                try { block() } catch (e: Throwable) { Log.w(TAG, "Session DB write failed", e) }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "SnoreNudge"
        const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000
    }
}
