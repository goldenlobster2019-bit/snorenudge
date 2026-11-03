package ro.goldenlobster.snorenudge.screenshots

import ro.goldenlobster.snorenudge.data.Aggregations
import ro.goldenlobster.snorenudge.data.NightSession
import ro.goldenlobster.snorenudge.data.Nudge
import ro.goldenlobster.snorenudge.data.SnoreEventEntity
import ro.goldenlobster.snorenudge.service.MonitorSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** Deterministic fake data for the screenshot tests (fixed clock, fixed seed). */
object SampleData {
    const val ZONE = "Europe/Bucharest"
    private val zone = ZoneId.of(ZONE)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    fun min(m: Int): Long = m * 60_000L

    /** "Now" for the running screens: 01:30 on 3 Nov 2026. */
    val NOW: Long = at(2026, 11, 3, 1, 30)
    val TODAY: LocalDate = LocalDate.of(2026, 11, 3)

    fun idle() = MonitorSnapshot()

    /** Running session; startedAt is derived from [NOW] so the elapsed time shown is exactly [elapsedMs]. */
    fun running(elapsedMs: Long, delayRemainingMs: Long) = MonitorSnapshot(
        running = true,
        startedAt = NOW - elapsedMs,
        lastDb = -48f,
        noiseFloorDb = -62f,
        snoreEventCount = 37,
        nudgeCount = 9,
        lastNudgeAt = NOW - min(11),
        lastNudgeLevel = 2,
        startDelayRemainingMs = delayRemainingMs,
        plannedEndAt = at(2026, 11, 3, 7, 30),
    )

    fun liveTest() = MonitorSnapshot(
        running = true,
        startedAt = NOW - min(3),
        lastDb = -41.3f,
        noiseFloorDb = -63.8f,
        gateOpen = true,
        gateOpenRatio = 0.31f,
        snoreScore = 0.82f,
        breathingScore = 0.47f,
        speechScore = 0.03f,
        topLabel = "Snoring",
        topScore = 0.82f,
        inferenceCount = 214,
        lastInferenceMs = 38L,
        positiveFrame = true,
        competitorScore = 0.12f,
        snoreEventCount = 5,
        nudgeCount = 1,
        plannedEndAt = at(2026, 11, 3, 7, 30),
    )

    fun lastNight() = night().session

    class Night(val session: NightSession, val events: List<SnoreEventEntity>, val nudges: List<Nudge>)

    /** Night of 2 -> 3 Nov, 23:12 to 07:05, with three snoring clusters and nine nudges. */
    fun night(): Night {
        val start = at(2026, 11, 2, 23, 12)
        val end = at(2026, 11, 3, 7, 5)
        val rnd = Random(7)
        // (offset from start in minutes, length in minutes, events per minute x10)
        val clusters = listOf(Triple(45, 40, 6), Triple(170, 55, 9), Triple(310, 70, 7))
        val events = ArrayList<SnoreEventEntity>()
        for ((off, len, rate) in clusters) {
            var t = start + min(off)
            val stop = start + min(off + len)
            while (t < stop) {
                events += SnoreEventEntity(
                    sessionId = 1, ts = t, durationMs = 1_500L + rnd.nextLong(2_500L),
                    maxScore = 0.35f + rnd.nextFloat() * 0.55f, peakDb = -55f + rnd.nextFloat() * 20f,
                )
                t += 60_000L * 10 / rate + rnd.nextLong(4_000L)
            }
        }
        val nudgeOffsets = listOf(52 to 1, 63 to 1, 181 to 1, 190 to 2, 203 to 2, 322 to 1, 331 to 2, 346 to 3, 362 to 3)
        val nudges = nudgeOffsets.map { (off, lvl) -> Nudge(sessionId = 1, ts = start + min(off), level = lvl) }
        val session = NightSession(
            id = 1, startedAt = start, endedAt = end, endReason = "stopAt", testMode = false,
            settingsSummary = "thr=0.30 events=3 cooldown=30s delay=20m stop=07:30 vib=Medium max=150",
            snoreEvents = events.size, nudges = nudges.size,
            snoringMinutes = Aggregations.snoringMinutes(events.map { it.ts }),
            gateOpenRatio = 0.27f, avgNoiseFloorDb = -61.4f, avgInferenceMs = 36f,
        )
        return Night(session, events, nudges)
    }

    /** 30 finished nights, newest first (as the DAO returns them); two of them test-mode runs. */
    fun history(): List<NightSession> {
        val rnd = Random(11)
        val reasons = listOf("stopAt", "user", "stopAt", "maxSession", "stopAt", "lowBattery", "stopAt")
        return List(30) { i ->
            val day = TODAY.minusDays(i.toLong())
            val startH = 22 + rnd.nextInt(2)
            val startM = rnd.nextInt(60)
            val start = LocalDateTime.of(day.minusDays(1), java.time.LocalTime.of(startH, startM)).atZone(zone).toInstant().toEpochMilli()
            val test = i == 4 || i == 17
            val lenMin = if (test) 18 + rnd.nextInt(30) else 330 + rnd.nextInt(180)
            val minutes = when {
                test -> 6 + rnd.nextInt(8)
                i % 9 == 5 -> 0
                else -> 8 + rnd.nextInt(70)
            }
            val nudges = if (test) 2 + rnd.nextInt(3) else minutes / 7 + rnd.nextInt(4)
            NightSession(
                id = (30 - i).toLong(), startedAt = start, endedAt = start + min(lenMin),
                endReason = if (test) "user" else reasons[i % reasons.size], testMode = test,
                snoreEvents = minutes * 2 + rnd.nextInt(10), nudges = nudges, snoringMinutes = minutes,
                gateOpenRatio = 0.2f, avgNoiseFloorDb = -60f, avgInferenceMs = 35f,
            )
        }
    }
}
