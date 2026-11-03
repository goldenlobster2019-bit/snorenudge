package ro.goldenlobster.snorenudge.haptics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.goldenlobster.snorenudge.data.VibrationStrength

class PatternsTest {
    @Test fun mediumKeepsCurrentValues() {
        val s = VibrationStrength.MEDIUM.scale
        assertEquals(120, Patterns.forLevel(1, s).amplitudes.max())
        assertEquals(180, Patterns.forLevel(2, s).amplitudes.max())
        assertEquals(255, Patterns.forLevel(3, s).amplitudes.max())
    }

    @Test fun lowScalesDown() {
        val s = VibrationStrength.LOW.scale
        assertEquals(72, Patterns.forLevel(1, s).amplitudes.max())
        assertEquals(108, Patterns.forLevel(2, s).amplitudes.max())
        assertEquals(153, Patterns.forLevel(3, s).amplitudes.max())
    }

    @Test fun highClampsTo255() {
        val s = VibrationStrength.HIGH.scale
        assertEquals(168, Patterns.forLevel(1, s).amplitudes.max())
        assertEquals(252, Patterns.forLevel(2, s).amplitudes.max())
        assertEquals(255, Patterns.forLevel(3, s).amplitudes.max())
    }

    @Test fun scaleIsClampedTo1And255() {
        assertEquals(1, Patterns.scaleAmplitude(1, 0.1f))
        assertEquals(255, Patterns.scaleAmplitude(200, 5f))
        assertEquals(0, Patterns.scaleAmplitude(0, 5f))
    }

    @Test fun level3IsFivePulsesOfAboutThreeSeconds() {
        val p = Patterns.forLevel(3)
        // 0 ms lead-in, then 5 x 400 ms on with 4 x 200 ms gaps.
        assertArrayEquals(longArrayOf(0, 400, 200, 400, 200, 400, 200, 400, 200, 400), p.timings)
        assertArrayEquals(intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255), p.amplitudes)
        assertTrue(Patterns.totalMs(p) in 2_500L..3_500L)
    }

    @Test fun level3IsLongerThanLevel2AndLevel1() {
        val t1 = Patterns.totalMs(Patterns.forLevel(1))
        val t2 = Patterns.totalMs(Patterns.forLevel(2))
        val t3 = Patterns.totalMs(Patterns.forLevel(3))
        assertTrue(t1 < t2 && t2 < t3)
    }

    @Test fun level1PatternStillAvailable() {
        val p = Patterns.forLevel(1)
        assertArrayEquals(longArrayOf(0, 250, 300, 250), p.timings)
        assertArrayEquals(intArrayOf(0, 120, 0, 120), p.amplitudes)
    }

    @Test fun durationsAndGapsUnchanged() {
        for (level in 1..3) {
            val base = Patterns.forLevel(level)
            for (st in VibrationStrength.entries) {
                val p = Patterns.forLevel(level, st.scale)
                assertArrayEquals(base.timings, p.timings)
                for (i in p.amplitudes.indices) {
                    if (base.amplitudes[i] == 0) assertEquals(0, p.amplitudes[i])
                    else assertTrue(p.amplitudes[i] in 1..255)
                }
            }
        }
    }
}
