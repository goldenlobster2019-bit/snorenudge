package ro.goldenlobster.snorenudge.detect

enum class SuppressReason(val label: String) {
    START_DELAY("startDelay"),
    COOLDOWN("cooldown"),
    MAX_REACHED("maxReached"),
}

sealed class EpisodeDecision {
    object None : EpisodeDecision()
    data class Nudge(val level: Int) : EpisodeDecision()
    data class Suppressed(val reason: SuppressReason) : EpisodeDecision()
}

/** Turns closed snore events into nudge decisions. Pure Kotlin, injected timestamps. */
class EpisodeDetector(private val config: DetectionConfig, private val sessionStartMs: Long) {
    private val starts = ArrayDeque<Long>()
    private var lastNudgeMs = Long.MIN_VALUE
    private var lastLevel = 0
    private var lastEpisodeMs = Long.MIN_VALUE

    var nudgeCount = 0
        private set
    var suppressedStartDelay = 0
        private set
    var suppressedCooldown = 0
        private set
    var suppressedMax = 0
        private set

    val suppressedTotal: Int get() = suppressedStartDelay + suppressedCooldown + suppressedMax

    /** Call when an event closes; nowMs is the current time (close time). */
    fun onEvent(event: SnoreEvent, nowMs: Long): EpisodeDecision {
        starts.addLast(event.startMs)
        while (starts.isNotEmpty() && nowMs - starts.first() > config.episodeWindowMs) starts.removeFirst()
        if (starts.size < config.eventsToTrigger) return EpisodeDecision.None

        // An episode happened. Either nudge or suppress; both consume the event window.
        starts.clear()
        if (lastEpisodeMs != Long.MIN_VALUE && nowMs - lastEpisodeMs >= config.escalationResetMs) lastLevel = 0
        lastEpisodeMs = nowMs

        val reason = when {
            nowMs < sessionStartMs + config.startDelayMs -> SuppressReason.START_DELAY
            lastNudgeMs != Long.MIN_VALUE && nowMs - lastNudgeMs < config.cooldownMs -> SuppressReason.COOLDOWN
            nudgeCount >= config.maxNudgesPerNight -> SuppressReason.MAX_REACHED
            else -> null
        }
        if (reason != null) {
            when (reason) {
                SuppressReason.START_DELAY -> suppressedStartDelay++
                SuppressReason.COOLDOWN -> suppressedCooldown++
                SuppressReason.MAX_REACHED -> suppressedMax++
            }
            return EpisodeDecision.Suppressed(reason)
        }

        val level = if (lastLevel > 0 && lastNudgeMs != Long.MIN_VALUE &&
            nowMs - lastNudgeMs <= config.escalationWindowMs
        ) minOf(lastLevel + 1, 3) else 1
        lastLevel = level
        lastNudgeMs = nowMs
        nudgeCount++
        return EpisodeDecision.Nudge(level)
    }
}
