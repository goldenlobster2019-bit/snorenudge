package ro.goldenlobster.snorenudge.ui.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

internal fun dateLabel(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DATE.format(Instant.ofEpochMilli(ms).atZone(zone))

internal fun shortDateLabel(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    SHORT_DATE.format(Instant.ofEpochMilli(ms).atZone(zone))

internal fun timeLabel(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    TIME.format(Instant.ofEpochMilli(ms).atZone(zone))

/** h:mm */
internal fun durationLabel(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0L) + 30_000L) / 60_000L
    return String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60)
}

internal fun endReasonLabel(reason: String?): String = when (reason) {
    "user", "stopped" -> "Stopped manually"
    "stopAt" -> "Auto-stop time"
    "maxSession" -> "Max length reached"
    "lowBattery" -> "Low battery"
    "interrupted" -> "Interrupted"
    null -> "Running"
    else -> reason
}
