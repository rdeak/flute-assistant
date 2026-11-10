// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The writing rules by themselves, no clock and no coroutines: at 120 bpm a quarter note is 500 ms. */
class TuneWriterTest {

    private val pulse = Pulse(120)
    private val writer = TuneWriter()

    /** Presses [midi] at [at], holds it for [heldMs] and lets go. */
    private fun tap(midi: Int?, at: Long, heldMs: Long) {
        writer.press(midi, at, pulse)
        writer.release(at + heldMs)
    }

    @Test
    fun `a press adds an eighth, which takes the length of the hold when let go`() {
        val press = writer.press(60, 0, pulse)
        assertEquals(TuneWriter.Press(ended = null, rejoinGapMs = null, elapsedMs = 0), press)
        assertEquals(listOf(TuneNote(60, EIGHTH)), writer.tune)
        assertEquals(Held(60, index = 0), writer.held)
        writer.grow(QUARTER)
        assertEquals(listOf(TuneNote(60, QUARTER)), writer.tune)
        assertEquals(TuneWriter.Ended(60, heldMs = 1000, length = HALF, added = true), writer.release(1000))
        assertEquals(listOf(TuneNote(60, HALF)), writer.tune)
        assertNull(writer.held)
        assertNull("nothing held: nothing ends", writer.release(2000))
    }

    @Test
    fun `the rest key writes a rest the same way`() {
        writer.press(null, 0, pulse)
        assertEquals(Held(midi = null, index = 0), writer.held)
        assertEquals(TuneWriter.Ended(null, heldMs = 500, length = QUARTER, added = true), writer.release(500))
        assertEquals(listOf(TuneRest(QUARTER)), writer.tune)
    }

    @Test
    fun `a press over a held key ends that key first`() {
        writer.press(60, 0, pulse)
        val press = writer.press(62, 500, pulse)
        assertEquals(TuneWriter.Ended(60, heldMs = 500, length = QUARTER, added = true), press.ended)
        assertNull(press.rejoinGapMs)
        assertEquals(listOf(TuneNote(60, QUARTER), TuneNote(62, EIGHTH)), writer.tune)
        assertEquals(Held(62, index = 1), writer.held)
    }

    @Test
    fun `a note key pressed again inside the window goes on with its note, gap included`() {
        tap(60, at = 0, heldMs = 100)
        val press = writer.press(60, 100 + TuneWriter.HOLD_REJOIN_MS, pulse)
        assertEquals(TuneWriter.HOLD_REJOIN_MS, press.rejoinGapMs)
        assertEquals("counted from the first press", 100 + TuneWriter.HOLD_REJOIN_MS, press.elapsedMs)
        assertEquals(Held(60, index = 0), writer.held)
        writer.release(500)
        assertEquals(listOf(TuneNote(60, QUARTER)), writer.tune)
    }

    @Test
    fun `a note key pressed again past the window is a new note`() {
        tap(60, at = 0, heldMs = 100)
        val press = writer.press(60, 100 + TuneWriter.HOLD_REJOIN_MS + 1, pulse)
        assertNull(press.rejoinGapMs)
        assertEquals(2, writer.tune.size)
    }

    @Test
    fun `the rest key goes on with its rest however long the pause, from the length it has`() {
        tap(null, at = 0, heldMs = 500) // a quarter rest
        val press = writer.press(null, 5_000, pulse)
        assertEquals(4_500L, press.rejoinGapMs)
        assertEquals("the pause is not counted", 500L, press.elapsedMs)
        writer.release(5_500) // another 500 ms: a half rest in all
        assertEquals(listOf(TuneRest(HALF)), writer.tune)
    }

    @Test
    fun `forget, another key, a breath, an erase and another book each end the chance to go on`() {
        val acts: List<(TuneWriter) -> Unit> = listOf(
            { it.forget() },
            { it.press(62, 101, pulse); it.release(102) },
            { it.breath() },
            { it.erase() },
            { it.open(listOf(TuneNote(60, EIGHTH))) },
        )
        for (act in acts) {
            val writer = TuneWriter()
            writer.press(60, 0, pulse)
            writer.release(100)
            act(writer)
            assertNull(writer.press(60, 110, pulse).rejoinGapMs)
        }
    }

    @Test
    fun `at the cap a key is held but nothing is added`() {
        val full = TuneWriter(List(Tune.MAX_NOTES) { TuneNote(60, EIGHTH) })
        full.press(62, 0, pulse)
        assertEquals(Held(62, index = null), full.held)
        full.grow(WHOLE)
        assertEquals(TuneWriter.Ended(62, heldMs = 500, length = QUARTER, added = false), full.release(500))
        assertEquals(Tune.MAX_NOTES, full.tune.size)
        assertFalse(full.breath())
    }

    @Test
    fun `a breath is never added twice in a row`() {
        tap(60, at = 0, heldMs = 100)
        assertTrue(writer.breath())
        assertFalse(writer.breath())
        assertEquals(listOf(TuneNote(60, EIGHTH), TuneBreath), writer.tune)
    }

    @Test
    fun `erase drops the last step, but not while a key is held or from an empty tune`() {
        assertFalse(writer.erase())
        tap(60, at = 0, heldMs = 100)
        writer.press(62, 1_000, pulse)
        assertFalse(writer.erase())
        writer.release(1_100)
        assertTrue(writer.erase())
        assertEquals(listOf(TuneNote(60, EIGHTH)), writer.tune)
    }
}
