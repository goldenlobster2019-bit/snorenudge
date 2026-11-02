package ro.goldenlobster.snorenudge.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameClassifierTest {
    private val c = DetectionConfig()

    @Test fun positiveWhenAllConditionsMet() =
        assertTrue(FrameClassifier.isPositive(0.5f, 0.1f, -30f, -50f, c))

    @Test fun belowThresholdIsNegative() =
        assertFalse(FrameClassifier.isPositive(0.29f, 0.0f, -30f, -50f, c))

    @Test fun thresholdIsInclusive() =
        assertTrue(FrameClassifier.isPositive(0.30f, 0.0f, -30f, -50f, c))

    @Test fun competitorTooCloseIsNegative() =
        assertFalse(FrameClassifier.isPositive(0.45f, 0.42f, -30f, -50f, c))

    @Test fun competitorWinsIsNegative() =
        assertFalse(FrameClassifier.isPositive(0.6f, 0.7f, -30f, -50f, c))

    @Test fun dominanceMarginIsInclusive() =
        assertTrue(FrameClassifier.isPositive(0.5f, 0.25f, -30f, -50f, c))

    @Test fun quietPartnerLevelIsNegative() =
        assertFalse(FrameClassifier.isPositive(0.6f, 0.0f, -45f, -50f, c))

    @Test fun loudnessMarginIsInclusive() =
        assertTrue(FrameClassifier.isPositive(0.6f, 0.0f, -40f, -50f, c))
}
