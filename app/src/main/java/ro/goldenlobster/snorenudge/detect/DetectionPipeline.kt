package ro.goldenlobster.snorenudge.detect

/** Outcome of feeding one frame or tick. */
class PipelineResult(
    val positive: Boolean,
    val event: SnoreEvent?,
    val decision: EpisodeDecision,
)

/** FrameClassifier -> EventBuilder -> EpisodeDetector. Not thread-safe; callers synchronize. */
class DetectionPipeline(val config: DetectionConfig, sessionStartMs: Long) {
    private val builder = EventBuilder(config.mergeGapMs)
    val episodes = EpisodeDetector(config, sessionStartMs)
    private var lastNowMs = Long.MIN_VALUE

    var eventCount = 0
        private set

    fun onFrame(nowMs: Long, snore: Float, competitor: Float, windowPeakDb: Float, noiseFloorDb: Float): PipelineResult {
        val t = monotonic(nowMs)
        val positive = FrameClassifier.isPositive(snore, competitor, windowPeakDb, noiseFloorDb, config)
        return finish(positive, builder.onFrame(t, positive, snore, windowPeakDb), t)
    }

    /** Negative time step for hops where no inference ran (gate closed). */
    fun tick(nowMs: Long): PipelineResult {
        val t = monotonic(nowMs)
        return finish(false, builder.tick(t), t)
    }

    private fun finish(positive: Boolean, closed: SnoreEvent?, t: Long): PipelineResult {
        var decision: EpisodeDecision = EpisodeDecision.None
        if (closed != null) {
            eventCount++
            decision = episodes.onEvent(closed, t)
        }
        return PipelineResult(positive, closed, decision)
    }

    private fun monotonic(nowMs: Long): Long {
        if (nowMs > lastNowMs) lastNowMs = nowMs
        return lastNowMs
    }
}
