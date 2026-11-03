package ro.goldenlobster.snorenudge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.goldenlobster.snorenudge.detect.DetectionConfig

class AppSettingsTest {
    @Test fun defaultsMatchDetectionConfigDefaults() {
        assertEquals(DetectionConfig(), AppSettings().toDetectionConfig(false))
    }

    @Test fun sensitivityMapsToThreshold() {
        assertEquals(0.45f, AppSettings(sensitivity = Sensitivity.LOW).toDetectionConfig(false).scoreThreshold)
        assertEquals(0.30f, AppSettings(sensitivity = Sensitivity.MEDIUM).toDetectionConfig(false).scoreThreshold)
        assertEquals(0.20f, AppSettings(sensitivity = Sensitivity.HIGH).toDetectionConfig(false).scoreThreshold)
    }

    @Test fun allFieldsMapped() {
        val c = AppSettings(
            sensitivity = Sensitivity.HIGH, cooldownSec = 180, startDelayMin = 45, eventsToTrigger = 5, maxNudges = 40,
        ).toDetectionConfig(false)
        assertEquals(180_000L, c.cooldownMs)
        assertEquals(45 * 60_000L, c.startDelayMs)
        assertEquals(5, c.eventsToTrigger)
        assertEquals(40, c.maxNudgesPerNight)
    }

    @Test fun testModeOverridesDelayAndCooldownOnly() {
        val c = AppSettings(cooldownSec = 120, startDelayMin = 60, eventsToTrigger = 4).toDetectionConfig(true)
        assertEquals(0L, c.startDelayMs)
        assertEquals(30_000L, c.cooldownMs)
        assertEquals(4, c.eventsToTrigger)
    }

    @Test fun optionListsAndDefaults() {
        val d = AppSettings()
        assertTrue(d.cooldownSec in AppSettings.COOLDOWNS)
        assertTrue(d.startDelayMin in AppSettings.START_DELAYS)
        assertTrue(d.maxNudges in AppSettings.MAX_NUDGES)
        assertTrue(d.autoStopMinutes in AppSettings.AUTO_STOPS)
        assertEquals(14, AppSettings.AUTO_STOPS.size) // off + 06:00..09:00 in 15 min steps
        assertEquals(360, AppSettings.AUTO_STOPS[1])
        assertEquals(540, AppSettings.AUTO_STOPS.last())
        assertEquals("07:30", AppSettings.formatStop(d.autoStopMinutes))
        assertEquals("off", AppSettings.formatStop(AppSettings.STOP_OFF))
    }

    @Test fun newDefaultsAndOptions() {
        val d = AppSettings()
        assertEquals(30, d.cooldownSec)
        assertEquals(150, d.maxNudges)
        assertEquals(listOf(15, 30, 45, 60, 90, 120, 180), AppSettings.COOLDOWNS)
        assertEquals(listOf(40, 80, 150, Int.MAX_VALUE), AppSettings.MAX_NUDGES)
        assertEquals("Unlimited", AppSettings.formatMax(Int.MAX_VALUE))
        assertEquals("80", AppSettings.formatMax(80))
    }

    @Test fun unlimitedMapsToIntMax() {
        val c = AppSettings(maxNudges = AppSettings.UNLIMITED).toDetectionConfig(false)
        assertEquals(Int.MAX_VALUE, c.maxNudgesPerNight)
        assertTrue(AppSettings(maxNudges = AppSettings.UNLIMITED).summary(false).contains("max=Unlimited"))
    }
}
