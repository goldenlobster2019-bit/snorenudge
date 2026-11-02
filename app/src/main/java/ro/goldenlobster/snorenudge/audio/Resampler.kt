package ro.goldenlobster.snorenudge.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 48 kHz -> 16 kHz decimator (factor 3) with a Hamming-windowed sinc low-pass FIR.
 * Stateful across chunks; chunk sizes may be arbitrary.
 */
class Resampler(
    private val factor: Int = 3,
    taps: Int = 63,
    sourceRate: Int = 48000,
    cutoffHz: Double = 7000.0,
) {
    private val coeffs: DoubleArray
    private val n: Int
    private val history: DoubleArray // circular, last n input samples
    private var histPos = 0
    private var phase = 0

    init {
        n = if (taps % 2 == 0) taps + 1 else taps
        val fc = cutoffHz / sourceRate
        val m = (n - 1) / 2.0
        coeffs = DoubleArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            val x = i - m
            val sinc = if (x == 0.0) 2 * fc else sin(2 * PI * fc * x) / (PI * x)
            val w = 0.54 - 0.46 * cos(2 * PI * i / (n - 1))
            coeffs[i] = sinc * w
            sum += coeffs[i]
        }
        for (i in 0 until n) coeffs[i] /= sum // unity DC gain
        history = DoubleArray(n)
    }

    /**
     * Filters and decimates [count] samples of [input] into [out] starting at index 0.
     * Returns the number of output samples (<= count / factor + 1).
     */
    fun process(input: FloatArray, count: Int, out: FloatArray): Int {
        var outN = 0
        for (i in 0 until count) {
            history[histPos] = input[i].toDouble()
            histPos++
            if (histPos == n) histPos = 0
            if (phase == 0) {
                // Newest sample is at histPos-1; coeff[0] applies to newest.
                var acc = 0.0
                var idx = histPos - 1
                for (k in 0 until n) {
                    if (idx < 0) idx += n
                    acc += coeffs[k] * history[idx]
                    idx--
                }
                out[outN++] = acc.toFloat()
            }
            phase++
            if (phase == factor) phase = 0
        }
        return outN
    }
}
