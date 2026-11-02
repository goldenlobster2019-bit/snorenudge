package ro.goldenlobster.snorenudge.detect

data class SnoreEvent(val startMs: Long, val endMs: Long, val maxScore: Float, val peakDb: Float) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Merges positive frames into events. Timestamps are injected (any monotonic clock).
 * Event end = time of its last positive frame; it closes once no positive frame arrived for > mergeGapMs.
 */
class EventBuilder(private val mergeGapMs: Long) {
    private var open = false
    private var startMs = 0L
    private var lastPosMs = 0L
    private var maxScore = 0f
    private var peakDb = Float.NEGATIVE_INFINITY

    /** Snapshot of the currently open event, or null. */
    val currentEvent: SnoreEvent?
        get() = if (open) SnoreEvent(startMs, lastPosMs, maxScore, peakDb) else null

    /** Feed one analysed frame. Returns an event that was closed by this call, if any. */
    fun onFrame(nowMs: Long, positive: Boolean, score: Float, peakDb: Float): SnoreEvent? {
        if (!positive) return tick(nowMs)
        var closed: SnoreEvent? = null
        if (open && nowMs - lastPosMs > mergeGapMs) {
            closed = currentEvent
            open = false
        }
        if (!open) {
            open = true
            startMs = nowMs
            maxScore = score
            this.peakDb = peakDb
        } else {
            if (score > maxScore) maxScore = score
            if (peakDb > this.peakDb) this.peakDb = peakDb
        }
        lastPosMs = nowMs
        return closed
    }

    /** Negative time step (also used when the gate is closed). Returns the event if it closes now. */
    fun tick(nowMs: Long): SnoreEvent? {
        if (open && nowMs - lastPosMs > mergeGapMs) {
            val ev = currentEvent
            open = false
            return ev
        }
        return null
    }
}
