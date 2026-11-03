package ro.goldenlobster.snorenudge.detect

/**
 * All detection thresholds in one place (CLAUDE.md section 9). Times in milliseconds.
 * mergeGapMs must stay above the inference hop (1 s) so one missed frame does not split an event.
 * maxNudgesPerNight = Int.MAX_VALUE means unlimited.
 */
data class DetectionConfig(
    val scoreThreshold: Float = 0.30f,
    /** Snore score must exceed the competitor score by this much. */
    val dominanceMargin: Float = 0.05f,
    val minSnoreDbAboveFloor: Float = 10f,
    val mergeGapMs: Long = 2_200L,
    val eventsToTrigger: Int = 3,
    val episodeWindowMs: Long = 60_000L,
    val cooldownMs: Long = 30_000L,
    val escalationWindowMs: Long = 300_000L,
    val escalationResetMs: Long = 600_000L,
    val startDelayMs: Long = 20L * 60_000L,
    val maxNudgesPerNight: Int = 150,
) {
    fun summary(): String =
        "thr=$scoreThreshold dom=$dominanceMargin dbAbove=$minSnoreDbAboveFloor merge=$mergeGapMs " +
            "events=$eventsToTrigger window=$episodeWindowMs cooldown=$cooldownMs " +
            "escWin=$escalationWindowMs escReset=$escalationResetMs delay=$startDelayMs max=$maxNudgesPerNight"

    companion object {
        fun testMode(): DetectionConfig = DetectionConfig(startDelayMs = 0L, cooldownMs = 30_000L)
    }
}
