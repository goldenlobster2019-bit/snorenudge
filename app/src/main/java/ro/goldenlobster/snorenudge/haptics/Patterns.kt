package ro.goldenlobster.snorenudge.haptics

/** Vibration pattern for one nudge level. Waveform timings start with an initial 0 ms delay. */
class Pattern(val timings: LongArray, val amplitudes: IntArray)

object Patterns {
    private const val GAP_MS = 300L

    private fun build(pulses: Int, onMs: Long, amp: Int): Pattern {
        val t = ArrayList<Long>()
        val a = ArrayList<Int>()
        t.add(0L); a.add(0)
        for (i in 0 until pulses) {
            if (i > 0) { t.add(GAP_MS); a.add(0) }
            t.add(onMs); a.add(amp)
        }
        return Pattern(t.toLongArray(), a.toIntArray())
    }

    fun forLevel(level: Int): Pattern = when (level) {
        1 -> build(2, 250L, 120)
        2 -> build(3, 350L, 180)
        else -> build(4, 500L, 255)
    }
}
