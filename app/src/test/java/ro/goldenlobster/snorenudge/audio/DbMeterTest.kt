package ro.goldenlobster.snorenudge.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DbMeterTest {
    @Test
    fun silenceReturnsFloor() {
        assertEquals(DbMeter.SILENCE_DB, DbMeter.rmsDbfs(ShortArray(1600)), 0.0001f)
        assertEquals(DbMeter.SILENCE_DB, DbMeter.rmsDbfs(ShortArray(0)), 0.0001f)
    }

    @Test
    fun fullScaleSquareIsNearZeroDb() {
        val s = ShortArray(1000) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(0f, DbMeter.rmsDbfs(s), 0.01f)
    }

    @Test
    fun halfAmplitudeIsMinusSixDb() {
        val s = ShortArray(1000) { 16384 }
        assertEquals(-6.02f, DbMeter.rmsDbfs(s), 0.01f)
    }

    @Test
    fun fullScaleSineIsMinusThreeDb() {
        val s = ShortArray(16000) { (32767 * sin(2 * PI * 440 * it / 16000)).toInt().toShort() }
        assertEquals(-3.01f, DbMeter.rmsDbfs(s), 0.05f)
    }

    @Test
    fun countLimitsSamplesUsed() {
        val s = ShortArray(100) { if (it < 50) 16384 else 0 }
        assertEquals(-6.02f, DbMeter.rmsDbfs(s, 50), 0.01f)
    }
}
