package ro.goldenlobster.snorenudge.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MonitorSnapshot(
    val running: Boolean = false,
    /** Wall-clock millis when the session started, 0 if not running. */
    val startedAt: Long = 0L,
    val lastDb: Float? = null,
    val chosenAudioSource: String? = null,
    val sampleRate: Int = 0,
    val error: String? = null,
    val noiseFloorDb: Float? = null,
    val gateOpen: Boolean = false,
    val gateOpenRatio: Float = 0f,
    val snoreScore: Float = 0f,
    val breathingScore: Float = 0f,
    val speechScore: Float = 0f,
    val topLabel: String? = null,
    val topScore: Float = 0f,
    val inferenceCount: Int = 0,
    /** Duration of the most recent inference in ms. */
    val lastInferenceMs: Long = 0L,
    /** True while the Live test screen is visible; forces inference regardless of the gate. */
    val liveTestActive: Boolean = false,
    /** UI-owned: use the test-mode detection config on the next start. Only editable while not running. */
    val testMode: Boolean = false,
    val snoreEventCount: Int = 0,
    val nudgeCount: Int = 0,
    val lastNudgeAt: Long = 0L,
    val lastNudgeLevel: Int = 0,
    val startDelayRemainingMs: Long = 0L,
    val positiveFrame: Boolean = false,
    val competitorScore: Float = 0f,
)

/** Process-wide state shared between the service (writer) and the UI (reader). */
object MonitorState {
    private val _state = MutableStateFlow(MonitorSnapshot())
    val state: StateFlow<MonitorSnapshot> = _state.asStateFlow()

    fun update(block: (MonitorSnapshot) -> MonitorSnapshot) = _state.update(block)

    fun setLiveTestActive(active: Boolean) = update { it.copy(liveTestActive = active) }

    /** Ignored while a session is running (config is captured at session start). */
    fun setTestMode(enabled: Boolean) = update { if (it.running) it else it.copy(testMode = enabled) }

    /** Clears session data but keeps UI-owned flags such as liveTestActive and testMode. */
    fun reset() {
        _state.update { MonitorSnapshot(liveTestActive = it.liveTestActive, testMode = it.testMode) }
    }
}
