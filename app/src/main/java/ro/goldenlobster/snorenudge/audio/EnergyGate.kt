package ro.goldenlobster.snorenudge.audio

/**
 * Energy gate with adaptive noise floor. Feed one dBFS value per ~100 ms chunk with an injected timestamp.
 * Floor = rolling 20th percentile of per-second levels over the last [floorWindowMs].
 */
class EnergyGate(
    var gateMarginDb: Float = 8f,
    private val holdMs: Long = 3_000L,
    private val floorWindowMs: Long = 5 * 60_000L,
    private val warmupMs: Long = 10_000L,
    private val percentile: Double = 0.20,
) {
    var noiseFloorDb: Float = DbMeter.SILENCE_DB
        private set
    var isOpen: Boolean = false
        private set

    private var firstTs = -1L
    private var lastTs = -1L
    private var lastLoudTs = Long.MIN_VALUE
    private var minSeen = Float.MAX_VALUE

    // One value per second: the mean-free minimum-robust choice is the max of the second's chunks'
    // would bias up; use the average dB of the second's chunks.
    private val secTs = ArrayDeque<Long>()
    private val secVal = ArrayDeque<Float>()
    private var curSecIndex = Long.MIN_VALUE
    private var curSum = 0.0
    private var curCount = 0

    private var openMs = 0L
    private var totalMs = 0L

    /** Fraction of time the gate was open since start (0..1). */
    val openRatio: Float get() = if (totalMs == 0L) 0f else openMs.toFloat() / totalMs

    fun update(levelDb: Float, nowMs: Long): Boolean {
        if (firstTs < 0) {
            firstTs = nowMs
            lastTs = nowMs
        }
        val dt = (nowMs - lastTs).coerceIn(0L, 1_000L)
        lastTs = nowMs

        if (levelDb < minSeen) minSeen = levelDb
        accumulate(levelDb, nowMs)

        val floor = computeFloor(nowMs, levelDb)
        noiseFloorDb = floor

        if (levelDb > floor + gateMarginDb) lastLoudTs = nowMs
        isOpen = lastLoudTs != Long.MIN_VALUE && nowMs - lastLoudTs <= holdMs

        totalMs += dt
        if (isOpen) openMs += dt
        return isOpen
    }

    private fun accumulate(levelDb: Float, nowMs: Long) {
        val sec = Math.floorDiv(nowMs, 1000L)
        if (curSecIndex == Long.MIN_VALUE) curSecIndex = sec
        if (sec != curSecIndex) {
            flushSecond()
            curSecIndex = sec
        }
        curSum += levelDb
        curCount++
        val cutoff = nowMs - floorWindowMs
        while (secTs.isNotEmpty() && secTs.first() < cutoff) {
            secTs.removeFirst()
            secVal.removeFirst()
        }
    }

    private fun flushSecond() {
        if (curCount > 0) {
            secTs.addLast(curSecIndex * 1000L)
            secVal.addLast((curSum / curCount).toFloat())
        }
        curSum = 0.0
        curCount = 0
    }

    private fun computeFloor(nowMs: Long, levelDb: Float): Float {
        if (nowMs - firstTs < warmupMs) return minSeen
        if (secVal.isEmpty()) return minSeen
        val sorted = secVal.toFloatArray().also { it.sort() }
        val idx = ((sorted.size - 1) * percentile).toInt()
        return sorted[idx]
    }
}
