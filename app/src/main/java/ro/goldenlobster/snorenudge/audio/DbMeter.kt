package ro.goldenlobster.snorenudge.audio

import kotlin.math.log10
import kotlin.math.sqrt

/** Pure-Kotlin level helpers. No Android dependencies. */
object DbMeter {
    /** Floor returned for silence / empty input. */
    const val SILENCE_DB = -120.0f

    private const val FULL_SCALE = 32768.0

    /** RMS level of PCM16 samples in dBFS (0 dBFS = full-scale square wave amplitude 32768). */
    fun rmsDbfs(samples: ShortArray, count: Int = samples.size): Float {
        val n = count.coerceIn(0, samples.size)
        if (n == 0) return SILENCE_DB
        var sum = 0.0
        for (i in 0 until n) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / n)
        if (rms <= 0.0) return SILENCE_DB
        val db = (20.0 * log10(rms / FULL_SCALE)).toFloat()
        return if (db < SILENCE_DB) SILENCE_DB else db
    }
}
