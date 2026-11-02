package ro.goldenlobster.snorenudge.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EventBuilderTest {
    private val b = EventBuilder(1_500L)

    @Test fun consecutiveFramesFormOneEvent() {
        assertNull(b.onFrame(0, true, 0.4f, -30f))
        assertNull(b.onFrame(500, true, 0.6f, -25f))
        assertNull(b.onFrame(1000, true, 0.5f, -28f))
        val open = b.currentEvent!!
        assertEquals(0L, open.startMs)
        assertEquals(1000L, open.endMs)
        assertEquals(0.6f, open.maxScore, 0f)
        assertEquals(-25f, open.peakDb, 0f)
    }

    @Test fun gapWithinMergeExtendsEvent() {
        b.onFrame(0, true, 0.4f, -30f)
        assertNull(b.onFrame(500, false, 0f, -60f))
        assertNull(b.onFrame(1000, false, 0f, -60f))
        assertNull(b.onFrame(1500, true, 0.5f, -30f)) // exactly mergeGap after last positive
        assertEquals(1500L, b.currentEvent!!.endMs)
        assertEquals(0L, b.currentEvent!!.startMs)
    }

    @Test fun closesAfterGapExceeded() {
        b.onFrame(0, true, 0.4f, -30f)
        assertNull(b.onFrame(1500, false, 0f, -60f))
        val ev = b.onFrame(2000, false, 0f, -60f)
        assertNotNull(ev)
        assertEquals(0L, ev!!.startMs)
        assertEquals(0L, ev.endMs)
        assertNull(b.currentEvent)
    }

    @Test fun tickClosesEvent() {
        b.onFrame(0, true, 0.4f, -30f)
        b.onFrame(500, true, 0.4f, -30f)
        assertNull(b.tick(2000))
        val ev = b.tick(2001)
        assertNotNull(ev)
        assertEquals(500L, ev!!.endMs)
        assertNull(b.tick(5000))
    }

    @Test fun lateFrameClosesOldAndStartsNew() {
        b.onFrame(0, true, 0.4f, -30f)
        val closed = b.onFrame(4000, true, 0.5f, -20f)
        assertNotNull(closed)
        assertEquals(0L, closed!!.startMs)
        assertEquals(4000L, b.currentEvent!!.startMs)
    }

    @Test fun negativeWithoutEventDoesNothing() {
        assertNull(b.onFrame(0, false, 0f, -60f))
        assertNull(b.currentEvent)
    }
}
