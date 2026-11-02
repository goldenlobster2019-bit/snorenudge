package ro.goldenlobster.snorenudge.audio

import org.junit.Assert.*
import org.junit.Test

class RingBufferTest {
    @Test fun readsLatestWithoutWrap() {
        val rb = RingBuffer(10)
        rb.write(floatArrayOf(1f, 2f, 3f, 4f))
        val d = FloatArray(3)
        assertTrue(rb.readLatest(d))
        assertArrayEquals(floatArrayOf(2f, 3f, 4f), d, 0f)
    }

    @Test fun wrapsAround() {
        val rb = RingBuffer(5)
        rb.write(floatArrayOf(1f, 2f, 3f, 4f))
        rb.write(floatArrayOf(5f, 6f, 7f))
        val d = FloatArray(5)
        assertTrue(rb.readLatest(d))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f, 7f), d, 0f)
        assertEquals(5, rb.size)
    }

    @Test fun notEnoughData() {
        val rb = RingBuffer(5)
        rb.write(floatArrayOf(1f, 2f))
        assertFalse(rb.readLatest(FloatArray(3)))
    }

    @Test fun writeLargerThanCapacity() {
        val rb = RingBuffer(3)
        rb.write(floatArrayOf(1f, 2f, 3f, 4f, 5f))
        val d = FloatArray(3)
        assertTrue(rb.readLatest(d))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f), d, 0f)
    }
}
