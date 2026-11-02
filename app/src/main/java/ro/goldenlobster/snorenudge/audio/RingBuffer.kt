package ro.goldenlobster.snorenudge.audio

/** Fixed-capacity float ring buffer. Not thread-safe: use from one thread. */
class RingBuffer(val capacity: Int) {
    private val data = FloatArray(capacity)
    private var writePos = 0
    var size: Int = 0
        private set

    /** Total samples ever written. */
    var totalWritten: Long = 0
        private set

    fun write(src: FloatArray, count: Int = src.size) {
        val n = count.coerceIn(0, src.size)
        // Only the last `capacity` samples can survive.
        val start = if (n > capacity) n - capacity else 0
        for (i in start until n) {
            data[writePos] = src[i]
            writePos++
            if (writePos == capacity) writePos = 0
        }
        size = minOf(capacity, size + n)
        totalWritten += n
    }

    /**
     * Copies the latest `dst.size` samples (oldest first) into [dst].
     * Returns false (and leaves [dst] untouched) if fewer samples are available.
     */
    fun readLatest(dst: FloatArray): Boolean {
        val n = dst.size
        if (n > size) return false
        var pos = writePos - n
        if (pos < 0) pos += capacity
        for (i in 0 until n) {
            dst[i] = data[pos]
            pos++
            if (pos == capacity) pos = 0
        }
        return true
    }

    companion object {
        /** 2 s at 16 kHz. */
        const val DEFAULT_CAPACITY = 32000
    }
}
