package ro.goldenlobster.snorenudge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AggregationsTest {
    private val min = 60_000L

    @Test fun snoringMinutesCountsDistinctMinutes() {
        val base = 10_000 * min
        assertEquals(0, Aggregations.snoringMinutes(emptyList()))
        assertEquals(1, Aggregations.snoringMinutes(listOf(base, base + 5_000, base + 59_999)))
        assertEquals(2, Aggregations.snoringMinutes(listOf(base, base + 59_999, base + min)))
        assertEquals(3, Aggregations.snoringMinutes(listOf(base, base + 3 * min, base + 10 * min, base + 10 * min + 1)))
    }

    @Test fun bucketsGroupEventsPerTenMinutes() {
        val start = 1_000_000L
        val ts = listOf(start, start + 9 * min, start + 10 * min, start + 25 * min, start + 25 * min + 1)
        val b = Aggregations.buckets(start, start + 30 * min, ts)
        assertEquals(3, b.size)
        assertEquals(listOf(2, 1, 2), b.map { it.events })
        assertEquals(10 * min, b[1].startOffsetMs)
        assertEquals(20 * min, b[1].endOffsetMs)
    }

    @Test fun bucketsRoundUpPartialAndClampOutOfRange() {
        val start = 0L
        val b = Aggregations.buckets(start, 25 * min, listOf(-5 * min, 100 * min))
        assertEquals(3, b.size)
        assertEquals(listOf(1, 0, 1), b.map { it.events })
    }

    @Test fun bucketsAlwaysAtLeastOne() {
        assertEquals(1, Aggregations.buckets(5L, 5L, emptyList()).size)
    }

    @Test fun busiestSortsByCountThenTime() {
        val start = 0L
        val ts = listOf(0L, 11 * min, 12 * min, 13 * min, 21 * min, 22 * min, 31 * min, 32 * min)
        val top = Aggregations.busiest(Aggregations.buckets(start, 40 * min, ts), 2)
        assertEquals(listOf(1, 2), top.map { it.index }) // 3 events, then tie (2) resolved by earlier index
        assertEquals(3, top[0].events)
    }

    @Test fun busiestSkipsEmptyBuckets() {
        assertEquals(0, Aggregations.busiest(Aggregations.buckets(0L, 30 * min, emptyList()), 5).size)
    }

    @Test fun hourTicksAreFullLocalHours() {
        val zone = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 11, 2, 23, 20, 0, 0, zone).toInstant().toEpochMilli()
        val end = start + 3 * 60 * min
        val ticks = Aggregations.hourTicks(start, end, zone)
        assertEquals(listOf(40 * min, 100 * min, 160 * min), ticks)
    }

    @Test fun startDelayParsedFromSummary() {
        assertEquals(20, Aggregations.startDelayMinutes(AppSettings().summary(false)))
        assertEquals(0, Aggregations.startDelayMinutes(AppSettings().summary(true)))
        assertNull(Aggregations.startDelayMinutes("garbage"))
    }
}
