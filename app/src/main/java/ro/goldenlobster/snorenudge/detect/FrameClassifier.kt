package ro.goldenlobster.snorenudge.detect

/** Decides whether one analysed window counts as a snore frame. */
object FrameClassifier {
    fun isPositive(
        snore: Float,
        competitor: Float,
        windowPeakDb: Float,
        noiseFloorDb: Float,
        config: DetectionConfig,
    ): Boolean =
        snore >= config.scoreThreshold &&
            snore >= competitor + config.dominanceMargin &&
            windowPeakDb >= noiseFloorDb + config.minSnoreDbAboveFloor
}
