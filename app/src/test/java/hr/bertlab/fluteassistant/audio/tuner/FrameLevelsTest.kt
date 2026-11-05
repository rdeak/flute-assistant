package hr.bertlab.fluteassistant.audio.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FrameLevelsTest {

    private val rate = 48_000
    private val window = 4096
    private val hop = 2048

    /** A 440 Hz tone at about -20 dBFS, silent over [silentFrom] until [silentTo] (sample indices). */
    private fun tone(silentFrom: Int = 0, silentTo: Int = 0) = FloatArray(window) { i ->
        if (i in silentFrom until silentTo) 0f else (0.1 * sin(2 * PI * 440 * i / rate)).toFloat()
    }

    @Test
    fun `level maps RMS to a dB-style 0 to 1, silence to 0 and full scale to 1`() {
        assertEquals(0f, FrameLevels.level(FloatArray(window)), 0.001f)
        assertEquals(1f, FrameLevels.level(FloatArray(window) { 1f }), 0.001f)
        // A sine of 0.1 peak is -23 dBFS: (60 - 23) / 60.
        assertEquals(0.616f, FrameLevels.level(tone()), 0.01f)
    }

    @Test
    fun `the min level of a steady tone is its level`() {
        val w = tone()
        assertEquals(FrameLevels.level(w), FrameLevels.minLevel(w, hop), 0.02f)
    }

    @Test
    fun `the min level finds a 40 ms stop inside the newest hop that the window's level hides`() {
        // A tongued stop in the newest hop: the window's RMS over 85 ms hardly moves, the min does.
        val stopFrom = window - hop + 600
        val w = tone(silentFrom = stopFrom, silentTo = stopFrom + 40 * rate / 1000)
        assertTrue(FrameLevels.level(w) > 0.55f)
        assertTrue("min level ${FrameLevels.minLevel(w, hop)}", FrameLevels.minLevel(w, hop) < 0.2f)
    }

    @Test
    fun `the min level ignores a stop in the older half of the window`() {
        val w = tone(silentFrom = 100, silentTo = 100 + 40 * rate / 1000)
        assertEquals(FrameLevels.level(tone()), FrameLevels.minLevel(w, hop), 0.02f)
    }
}
