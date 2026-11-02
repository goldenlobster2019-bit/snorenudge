package ro.goldenlobster.snorenudge.detect

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object SessionTimes {
    /** Next local HH:mm strictly after startMs (same day if still ahead, otherwise next day). */
    fun nextLocalTimeAfter(startMs: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = Instant.ofEpochMilli(startMs).atZone(zone)
        var target = start.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zone)
        if (!target.toInstant().isAfter(start.toInstant())) {
            target = start.toLocalDate().plusDays(1).atTime(LocalTime.of(hour, minute)).atZone(zone)
        }
        return target.toInstant().toEpochMilli()
    }
}
