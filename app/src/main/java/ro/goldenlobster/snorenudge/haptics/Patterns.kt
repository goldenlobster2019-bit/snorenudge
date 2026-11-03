package ro.goldenlobster.snorenudge.haptics

import kotlin.math.roundToInt

/** Vibration pattern for one nudge level. Waveform timings start with an initial 0 ms delay. */
class Pattern(val timings: LongArray, val amplitudes: IntArray)

object Patterns {
    private const val GAP_MS = 300L

    /** Scales a non-zero amplitude by [scale], clamped to 1..255. Zero (off) stays zero. */
    fun scaleAmplitude(base: Int, scale: Float): Int =
        if (base <= 0) 0 else (base * scale).roundToInt().coerceIn(1, 255)

    private fun build(pulses: Int, onMs: Long, amp: Int, gapMs: Long = GAP_MS): Pattern {
        val t = ArrayList<Long>()
        val a = ArrayList<Int>()
        t.add(0L); a.add(0)
        for (i in 0 until pulses) {
            if (i > 0) { t.add(gapMs); a.add(0) }
            t.add(onMs); a.add(amp)
        }
        return Pattern(t.toLongArray(), a.toIntArray())
    }

    /** Total duration of a pattern in ms. */
    fun totalMs(p: Pattern): Long = p.timings.sum()

    /** Level 1 stays available for the low-battery buzz and the strength test; nudges start at level 2. */
    fun forLevel(level: Int, scale: Float = 1f): Pattern {
        val p = when (level) {
            1 -> build(2, 250L, 120)
            2 -> build(3, 350L, 180)
            else -> build(5, 400L, 255, 200L) // ~2.8 s of insistent pulses
        }
        if (scale == 1f) return p
        return Pattern(p.timings, IntArray(p.amplitudes.size) { scaleAmplitude(p.amplitudes[it], scale) })
    }
}
