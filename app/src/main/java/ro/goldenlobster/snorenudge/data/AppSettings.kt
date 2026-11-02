package ro.goldenlobster.snorenudge.data

import ro.goldenlobster.snorenudge.detect.DetectionConfig
import java.util.Locale

enum class Sensitivity(val label: String, val threshold: Float) {
    LOW("Low", 0.45f),
    MEDIUM("Medium", 0.30f),
    HIGH("High", 0.20f),
}

enum class VibrationStrength(val label: String, val scale: Float) {
    LOW("Low", 0.6f),
    MEDIUM("Medium", 1.0f),
    HIGH("High", 1.4f),
}

/** User settings; changes apply on the next session start. */
data class AppSettings(
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val cooldownSec: Int = 90,
    val startDelayMin: Int = 20,
    val eventsToTrigger: Int = 3,
    /** Minutes after midnight, or [STOP_OFF]. */
    val autoStopMinutes: Int = 7 * 60 + 30,
    val strength: VibrationStrength = VibrationStrength.MEDIUM,
    val maxNudges: Int = 40,
) {
    fun toDetectionConfig(testMode: Boolean): DetectionConfig {
        val base = DetectionConfig(
            scoreThreshold = sensitivity.threshold,
            eventsToTrigger = eventsToTrigger,
            cooldownMs = cooldownSec * 1_000L,
            startDelayMs = startDelayMin * 60_000L,
            maxNudgesPerNight = maxNudges,
        )
        return if (testMode) base.copy(startDelayMs = 0L, cooldownMs = 30_000L) else base
    }

    /** Compact description stored with the session; "delay=Nm" is parsed by the night detail chart. */
    fun summary(testMode: Boolean): String {
        val c = toDetectionConfig(testMode)
        return String.format(
            Locale.US, "thr=%.2f events=%d cooldown=%ds delay=%dm stop=%s vib=%s max=%d",
            c.scoreThreshold, c.eventsToTrigger, c.cooldownMs / 1000, c.startDelayMs / 60_000L,
            formatStop(autoStopMinutes), strength.label, c.maxNudgesPerNight,
        )
    }

    companion object {
        const val STOP_OFF = -1
        val COOLDOWNS = listOf(30, 60, 90, 120, 180, 300)
        val START_DELAYS = listOf(0, 10, 20, 30, 45, 60)
        val EVENTS = listOf(2, 3, 4, 5)
        val MAX_NUDGES = listOf(10, 20, 40, 60)
        val AUTO_STOPS: List<Int> = listOf(STOP_OFF) + (6 * 60..9 * 60 step 15)

        fun formatStop(minutes: Int): String =
            if (minutes < 0) "off" else String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }
}
