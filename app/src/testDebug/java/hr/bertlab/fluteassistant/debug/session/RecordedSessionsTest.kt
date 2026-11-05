package hr.bertlab.fluteassistant.debug.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Replays every session pinned under `src/testDebug/resources/sessions` (see its README) and
 * fails when one earns other marks than its pin line says. Gradle runs tests from the module
 * directory, so the path is relative to `app/`.
 */
class RecordedSessionsTest {

    @Test
    fun `every pinned session earns the marks its pin says`() {
        val dir = File("src/testDebug/resources/sessions")
        val pinned = dir.listFiles { f -> f.isDirectory }.orEmpty()
        if (pinned.isEmpty()) println("no pinned sessions in ${dir.absolutePath}")
        assertEquals(emptyList<String>(), RecordedSessions.check(dir))
    }
}
