package ro.goldenlobster.snorenudge.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DetectionPipelineTest {

    /** Periodic snoring: a positive frame pair every 4 s, 1.0 s hop; gate-closed hops use tick. */
    @Test fun periodicSnoringTriggersNudgeEndToEnd() {
        val p = DetectionPipeline(DetectionConfig(startDelayMs = 0L), 0L)
        var nudgeAt = -1L
        var t = 0L
        while (t <= 30_000L) {
            val inBreath = (t % 4_000L) < 2_000L
            val r = if (inBreath) p.onFrame(t, 0.6f, 0.05f, -30f, -50f) else p.tick(t)
            if (r.decision is EpisodeDecision.Nudge && nudgeAt < 0) nudgeAt = t
            t += 1_000
        }
        assertTrue(nudgeAt in 8_000L..14_000L)
        assertTrue(p.eventCount >= 3)
    }

    @Test fun speechCompetitorPreventsEvents() {
        val p = DetectionPipeline(DetectionConfig(startDelayMs = 0L), 0L)
        var t = 0L
        while (t <= 30_000L) {
            p.onFrame(t, 0.5f, 0.6f, -30f, -50f)
            t += 1_000
        }
        assertEquals(0, p.eventCount)
    }

    @Test fun nextLocalTimeAfter() {
        val zone = ZoneId.of("Europe/Bucharest")
        fun ms(h: Int, m: Int, day: Int = 10) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(ms(7, 30, 11), SessionTimes.nextLocalTimeAfter(ms(23, 0), 7, 30, zone))
        assertEquals(ms(7, 30, 10), SessionTimes.nextLocalTimeAfter(ms(2, 0), 7, 30, zone))
        assertEquals(ms(7, 30, 11), SessionTimes.nextLocalTimeAfter(ms(8, 0), 7, 30, zone))
        assertEquals(ms(7, 30, 11), SessionTimes.nextLocalTimeAfter(ms(7, 30), 7, 30, zone))
    }
}
