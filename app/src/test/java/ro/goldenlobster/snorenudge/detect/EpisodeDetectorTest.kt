package ro.goldenlobster.snorenudge.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeDetectorTest {
    private val noDelay = DetectionConfig(startDelayMs = 0L)

    private fun ev(start: Long) = SnoreEvent(start, start + 500, 0.5f, -30f)

    /** Feeds an event closing 2 s after its start. */
    private fun EpisodeDetector.feed(start: Long) = onEvent(ev(start), start + 2_000)

    private fun EpisodeDetector.feedAll(vararg starts: Long): EpisodeDecision {
        var last: EpisodeDecision = EpisodeDecision.None
        for (s in starts) last = feed(s)
        return last
    }

    @Test fun threeEventsInWindowTrigger() {
        val d = EpisodeDetector(noDelay, 0L)
        assertEquals(EpisodeDecision.None, d.feed(0))
        assertEquals(EpisodeDecision.None, d.feed(4_000))
        assertEquals(EpisodeDecision.Nudge(2), d.feed(8_000))
        assertEquals(1, d.nudgeCount)
    }

    @Test fun twoEventsDoNotTrigger() {
        val d = EpisodeDetector(noDelay, 0L)
        assertEquals(EpisodeDecision.None, d.feedAll(0, 4_000))
        assertEquals(0, d.nudgeCount)
    }

    @Test fun eventsSpreadBeyondWindowDoNotTrigger() {
        val d = EpisodeDetector(noDelay, 0L)
        // one event every 35 s: never 3 within 60 s
        for (i in 0 until 10) assertEquals(EpisodeDecision.None, d.feed(i * 35_000L))
        assertEquals(0, d.nudgeCount)
    }

    @Test fun cooldownSuppressesThenAllows() {
        val d = EpisodeDetector(noDelay, 0L)
        assertEquals(EpisodeDecision.Nudge(2), d.feedAll(0, 4_000, 8_000))
        // second episode 20 s later: inside the 30 s default cooldown
        assertEquals(
            EpisodeDecision.Suppressed(SuppressReason.COOLDOWN),
            d.feedAll(20_000, 24_000, 28_000),
        )
        assertEquals(1, d.suppressedCooldown)
        assertEquals(1, d.nudgeCount)
        // after cooldown
        assertEquals(EpisodeDecision.Nudge(3), d.feedAll(100_000, 104_000, 108_000))
    }

    @Test fun suppressedEpisodeClearsWindow() {
        val d = EpisodeDetector(noDelay, 0L)
        d.feedAll(0, 4_000, 8_000)
        d.feedAll(20_000, 24_000, 28_000) // suppressed
        // one more event must not immediately count as another episode
        assertEquals(EpisodeDecision.None, d.feed(32_000))
        assertEquals(1, d.suppressedTotal)
    }

    @Test fun startDelaySuppresses() {
        val d = EpisodeDetector(DetectionConfig(), 0L) // 20 min delay
        assertEquals(
            EpisodeDecision.Suppressed(SuppressReason.START_DELAY),
            d.feedAll(0, 4_000, 8_000),
        )
        assertEquals(1, d.suppressedStartDelay)
        assertEquals(0, d.nudgeCount)
        val after = 20 * 60_000L
        assertEquals(EpisodeDecision.Nudge(2), d.feedAll(after, after + 4_000, after + 8_000))
    }

    @Test fun escalationCapsAtThree() {
        val d = EpisodeDetector(noDelay, 0L)
        val levels = ArrayList<Int>()
        var t = 0L
        repeat(5) {
            val r = d.feedAll(t, t + 4_000, t + 8_000)
            levels.add((r as EpisodeDecision.Nudge).level)
            t += 100_000 // beyond cooldown, within 5 min of previous nudge
        }
        assertEquals(listOf(2, 3, 3, 3, 3), levels)
    }

    @Test fun escalationResetsAfterQuietPeriod() {
        val d = EpisodeDetector(noDelay, 0L)
        assertEquals(EpisodeDecision.Nudge(2), d.feedAll(0, 4_000, 8_000))
        assertEquals(EpisodeDecision.Nudge(3), d.feedAll(100_000, 104_000, 108_000))
        val later = 108_000L + 10 * 60_000L + 10_000L
        assertEquals(EpisodeDecision.Nudge(2), d.feedAll(later, later + 4_000, later + 8_000))
    }

    @Test fun escalationNotKeptBeyondEscalationWindow() {
        val d = EpisodeDetector(noDelay, 0L)
        d.feedAll(0, 4_000, 8_000)
        d.feedAll(100_000, 104_000, 108_000) // level 3
        val later = 108_000L + 6 * 60_000L // > 5 min, < 10 min
        assertEquals(EpisodeDecision.Nudge(2), d.feedAll(later, later + 4_000, later + 8_000))
    }

    @Test fun maxNudgesCap() {
        val d = EpisodeDetector(DetectionConfig(startDelayMs = 0L, maxNudgesPerNight = 2), 0L)
        var t = 0L
        val results = ArrayList<EpisodeDecision>()
        repeat(4) {
            results.add(d.feedAll(t, t + 4_000, t + 8_000))
            t += 100_000
        }
        assertTrue(results[0] is EpisodeDecision.Nudge)
        assertTrue(results[1] is EpisodeDecision.Nudge)
        assertEquals(EpisodeDecision.Suppressed(SuppressReason.MAX_REACHED), results[2])
        assertEquals(EpisodeDecision.Suppressed(SuppressReason.MAX_REACHED), results[3])
        assertEquals(2, d.nudgeCount)
        assertEquals(2, d.suppressedMax)
    }

    @Test fun testModeConfig() {
        val c = DetectionConfig.testMode()
        assertEquals(0L, c.startDelayMs)
        assertEquals(30_000L, c.cooldownMs)
        assertEquals(DetectionConfig().eventsToTrigger, c.eventsToTrigger)
    }

    @Test fun defaultsCooldown30sMax150() {
        val c = DetectionConfig()
        assertEquals(30_000L, c.cooldownMs)
        assertEquals(150, c.maxNudgesPerNight)
        assertEquals(300_000L, c.escalationWindowMs)
    }

    @Test fun renudgesThirtySecondsAfterSnoringResumes() {
        val d = EpisodeDetector(noDelay, 0L)
        assertEquals(EpisodeDecision.Nudge(2), d.feedAll(0, 4_000, 8_000)) // nudge at 10 s
        // snoring continues: episode closing at 25 s is still inside cooldown
        assertEquals(
            EpisodeDecision.Suppressed(SuppressReason.COOLDOWN),
            d.feedAll(15_000, 19_000, 23_000),
        )
        // snoring resumes later: first episode closing >= 30 s after the nudge nudges again, escalated
        assertEquals(EpisodeDecision.Nudge(3), d.feedAll(40_000, 44_000, 48_000))
    }

    @Test fun unlimitedMaxNeverReached() {
        val d = EpisodeDetector(DetectionConfig(startDelayMs = 0L, maxNudgesPerNight = Int.MAX_VALUE), 0L)
        var t = 0L
        repeat(200) {
            assertTrue(d.feedAll(t, t + 4_000, t + 8_000) is EpisodeDecision.Nudge)
            t += 100_000
        }
        assertEquals(0, d.suppressedMax)
    }

    @Test fun firstNudgeIsLevel2() {
        assertEquals(2, EpisodeDetector.FIRST_LEVEL)
    }
}
