package ro.goldenlobster.snorenudge.audio

import org.junit.Assert.*
import org.junit.Test

class EnergyGateTest {
    /** Feeds [seconds] of constant level in 100 ms steps starting at [t0]; returns end time. */
    private fun feed(g: EnergyGate, level: Float, seconds: Int, t0: Long): Long {
        var t = t0
        repeat(seconds * 10) { t += 100; g.update(level, t) }
        return t
    }

    @Test fun staysClosedOnSteadyNoise() {
        val g = EnergyGate()
        feed(g, -50f, 30, 0)
        assertFalse(g.isOpen)
        assertEquals(-50f, g.noiseFloorDb, 0.5f)
    }

    @Test fun opensOnLoud() {
        val g = EnergyGate()
        val t = feed(g, -50f, 30, 0)
        g.update(-30f, t + 100)
        assertTrue(g.isOpen)
    }

    @Test fun belowMarginDoesNotOpen() {
        val g = EnergyGate()
        val t = feed(g, -50f, 30, 0)
        g.update(-43f, t + 100) // 7 dB above, margin 8
        assertFalse(g.isOpen)
    }

    @Test fun holdsThreeSeconds() {
        val g = EnergyGate()
        var t = feed(g, -50f, 30, 0)
        t += 100
        g.update(-30f, t)
        val loud = t
        t += 2900; g.update(-50f, t)
        assertTrue(g.isOpen)
        t = loud + 3200; g.update(-50f, t)
        assertFalse(g.isOpen)
    }

    @Test fun floorAdaptsToNewNoiseLevel() {
        val g = EnergyGate()
        var t = feed(g, -60f, 60, 0)
        assertEquals(-60f, g.noiseFloorDb, 0.5f)
        // Fan turns on: new steady level; after > 5 min the old data is out of the window.
        t = feed(g, -40f, 400, t)
        assertEquals(-40f, g.noiseFloorDb, 0.5f)
        assertFalse(g.isOpen)
    }

    @Test fun usesMinimumDuringWarmup() {
        val g = EnergyGate()
        var t = 0L
        for (l in listOf(-45f, -50f, -48f)) { t += 100; g.update(l, t) }
        assertEquals(-50f, g.noiseFloorDb, 0.01f)
    }

    @Test fun openRatio() {
        val g = EnergyGate()
        var t = feed(g, -60f, 30, 0)
        // 10 s loud + 3 s hold = 13 s open out of 60 s total (~0.217)
        t = feed(g, -30f, 10, t)
        t = feed(g, -60f, 20, t)
        val r = g.openRatio
        assertTrue("ratio $r", r > 0.19f && r < 0.25f)
    }
}
