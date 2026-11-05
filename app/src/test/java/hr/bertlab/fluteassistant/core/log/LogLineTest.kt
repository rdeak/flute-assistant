package hr.bertlab.fluteassistant.core.log

import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LogLineTest {

    @Test
    fun `an event with no fields is just its name`() {
        assertEquals("mic.close", LogLine.format("mic.close"))
    }

    @Test
    fun `parse splits an event and its fields, unquoting text`() {
        val (event, fields) = LogLine.parse("session.start at=2026-11-05T18:32:10+01:00 device=\"Google Pixel 7\" floor=none n=2")
        assertEquals("session.start", event)
        assertEquals(mapOf("at" to "2026-11-05T18:32:10+01:00", "device" to "Google Pixel 7", "floor" to "none", "n" to "2"), fields)
        assertEquals("mic.close" to emptyMap<String, String>(), LogLine.parse("mic.close"))
    }

    @Test
    fun `fields are key=value pairs in the given order`() {
        assertEquals(
            "tuner.frame f=440.12 p=0.80 level=0.40 frames=25 ok=true mode=TARGET reading=none",
            LogLine.format(
                "tuner.frame",
                "f" to 440.123,
                "p" to 0.8,
                "level" to 0.4f,
                "frames" to 25,
                "ok" to true,
                "mode" to TunerMode.TARGET,
                "reading" to null,
            ),
        )
    }

    @Test
    fun `text with spaces is quoted so a line stays splittable on spaces`() {
        assertEquals("mic.error message=\"Microphone is busy\" code=-3", LogLine.format("mic.error", "message" to "Microphone is busy", "code" to -3))
        assertEquals("mic.open source=UNPROCESSED", LogLine.format("mic.open", "source" to "UNPROCESSED"))
    }
}
