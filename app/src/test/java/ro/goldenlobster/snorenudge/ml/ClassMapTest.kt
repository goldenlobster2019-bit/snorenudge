package ro.goldenlobster.snorenudge.ml

import org.junit.Assert.*
import org.junit.Test

class ClassMapTest {
    private val map = ClassMap.parse(javaClass.classLoader!!.getResourceAsStream("yamnet_class_map.csv")!!)

    @Test fun realFile() {
        assertEquals(521, map.size)
        assertEquals(38, map.indexOf("Snoring"))
        assertEquals(36, map.indexOf("Breathing"))
        assertEquals(0, map.indexOf("Speech"))
        assertEquals(-1, map.indexOf("Nonexistent"))
    }

    @Test fun quotedNamesWithCommas() {
        val csv = "index,mid,display_name\n0,/m/a,Speech\n1,/m/b,\"Child speech, kid speaking\"\n2,/m/c,\"Say \"\"hi\"\"\"\n"
        val m = ClassMap.parse(csv.byteInputStream())
        assertEquals("Child speech, kid speaking", m.names[1])
        assertEquals("Say \"hi\"", m.names[2])
        assertEquals(1, m.indexOf("Child speech, kid speaking"))
    }
}
