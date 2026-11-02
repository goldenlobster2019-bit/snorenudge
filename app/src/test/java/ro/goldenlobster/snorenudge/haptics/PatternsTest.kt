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
