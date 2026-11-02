package ro.goldenlobster.snorenudge.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class ResamplerTest {
    private fun run(freq: Double): FloatArray {
        val r = Resampler()
        val total = 48000
        val out = ArrayList<Float>()
        val chunk = FloatArray(4800)
        val tmp = FloatArray(1602)
        var t = 0
        while (t < total) {
            for (i in 0 until 4800) chunk[i] = sin(2 * PI * freq * (t + i) / 48000.0).toFloat()
            val n = r.process(chunk, 4800, tmp)
            for (i in 0 until n) out.add(tmp[i])
            t += 4800
        }
        return out.toFloatArray()
    }

    private fun rms(a: FloatArray, skip: Int): Double {
        var s = 0.0
        for (i in skip until a.size) s += a[i] * a[i]
        return sqrt(s / (a.size - skip))
    }

    @Test fun outputLength() {
        assertEquals(16000, run(1000.0).size)
    }

    @Test fun passbandKeepsAmplitude() {
        val peak = run(1000.0).drop(100).maxOf { abs(it) }
        assertEquals(1.0, peak.toDouble(), 0.05)
    }

    @Test fun stopbandAttenuated() {
        val inRms = 1.0 / sqrt(2.0)
        val db = 20 * log10(rms(run(12000.0), 100) / inRms)
        assertTrue("attenuation was $db dB", db < -30)
    }
}
