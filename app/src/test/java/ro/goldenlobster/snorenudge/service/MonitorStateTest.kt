package ro.goldenlobster.snorenudge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorStateTest {
    @Test fun testModeSwitchesOffWhenSessionEnds() {
        MonitorState.reset()
        MonitorState.setTestMode(true)
        assertTrue(MonitorState.state.value.testMode)
        MonitorState.update { it.copy(running = true) }
        MonitorState.setTestMode(false) // ignored while running
        assertTrue(MonitorState.state.value.testMode)
        MonitorState.setLiveTestActive(true)
        MonitorState.reset() // what the service does on stop
        assertFalse(MonitorState.state.value.testMode)
        assertFalse(MonitorState.state.value.running)
        assertTrue(MonitorState.state.value.liveTestActive) // UI-owned flag survives
        MonitorState.setLiveTestActive(false)
        assertEquals(0L, MonitorState.state.value.plannedEndAt)
    }
}
