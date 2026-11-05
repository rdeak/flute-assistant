package hr.bertlab.fluteassistant.core.log

import hr.bertlab.fluteassistant.fakes.FakeLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingLoggerTest {

    private val inner = FakeLogger()
    private val lines = mutableListOf<Triple<String, Char, String>>()
    private val logger = RecordingLogger(inner) { tag, level, line -> lines += Triple(tag, level, line) }

    @Test
    fun `d i w and e reach the inner logger and the sink with tag, level and the formatted line`() {
        logger.d(LogTags.FLUTE, "flute.heard", "index" to 0, "heard" to 71)
        logger.i(LogTags.TUNER, "mic.open", "source" to "MIC")
        val boom = IllegalStateException("boom")
        logger.w(LogTags.FLUTE, "flute.listen_error", "message" to "busy mic", error = boom)
        logger.e(LogTags.FLUTE, "flute.crash", error = boom)
        assertEquals(listOf("flute.heard", "mic.open", "flute.listen_error", "flute.crash"), inner.events.map { it.event })
        assertEquals(boom, inner.events[2].error)
        assertEquals(
            listOf(
                Triple(LogTags.FLUTE, 'd', "flute.heard index=0 heard=71"),
                Triple(LogTags.TUNER, 'i', "mic.open source=MIC"),
                Triple(LogTags.FLUTE, 'w', "flute.listen_error message=\"busy mic\""),
                Triple(LogTags.FLUTE, 'e', "flute.crash"),
            ),
            lines,
        )
    }

    @Test
    fun `verbose lines reach only the inner logger`() {
        inner.verbose = true
        logger.v(LogTags.TUNER, "tuner.frame", "f" to 440.0)
        assertEquals(listOf("tuner.frame"), inner.events.map { it.event })
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `isVerbose asks the inner logger`() {
        assertFalse(logger.isVerbose(LogTags.TUNER))
        inner.verbose = true
        assertTrue(logger.isVerbose(LogTags.TUNER))
    }
}
