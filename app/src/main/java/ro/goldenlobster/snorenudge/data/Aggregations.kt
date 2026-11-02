package ro.goldenlobster.snorenudge.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure helpers for session statistics and the timeline chart. */
object Aggregations {
    const val BUCKET_MS = 10L * 60_000L

    class Bucket(val index: Int, val startOffsetMs: Long, val endOffsetMs: Long, val events: Int)

    /** Number of distinct wall-clock minutes that contain at least one snore event. */
    fun snoringMinutes(eventTimestamps: Collection<Long>): Int =
        eventTimestamps.mapTo(HashSet()) { Math.floorDiv(it, 60_000L) }.size

    /**
     * Event counts per fixed-size bucket relative to [sessionStart]. Always returns at least one bucket;
     * events outside the range are clamped into the first or last bucket.
     */
    fun buckets(sessionStart: Long, sessionEnd: Long, eventTimestamps: Collection<Long>, bucketMs: Long = BUCKET_MS): List<Bucket> {
        val duration = (sessionEnd - sessionStart).coerceAtLeast(0L)
        val n = maxOf(1, ((duration + bucketMs - 1) / bucketMs).toInt())
        val counts = IntArray(n)
        for (ts in eventTimestamps) {
            val idx = ((ts - sessionStart) / bucketMs).toInt().coerceIn(0, n - 1)
            counts[idx]++
        }
        return List(n) { Bucket(it, it * bucketMs, (it + 1) * bucketMs, counts[it]) }
    }

    /** Buckets with at least one event, most events first (earlier first on ties). */
    fun busiest(buckets: List<Bucket>, limit: Int): List<Bucket> =
        buckets.filter { it.events > 0 }
            .sortedWith(compareByDescending<Bucket> { it.events }.thenBy { it.index })
            .take(limit)

    /** Offsets (ms from [start]) of each full local hour strictly after start, up to and including [end]. */
    fun hourTicks(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        if (end <= start) return emptyList()
        val out = ArrayList<Long>()
        var t = Instant.ofEpochMilli(start).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (t.toInstant().toEpochMilli() <= end) {
            out.add(t.toInstant().toEpochMilli() - start)
            t = t.plusHours(1)
        }
        return out
    }

    /** Extracts the start delay in minutes from a settings summary ("delay=20m"), or null. */
    fun startDelayMinutes(summary: String): Int? =
        Regex("""delay=(\d+)m""").find(summary)?.groupValues?.get(1)?.toIntOrNull()
}
