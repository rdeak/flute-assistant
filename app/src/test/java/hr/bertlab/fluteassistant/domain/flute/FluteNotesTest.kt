// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flute's own notes: every semitone from C4 to C7 (since 2026-11-18), a black note on its letter's place, and what a key writes. */
class FluteNotesTest {

    @Test
    fun `the flute has every semitone from c4 to c7, the tuner's range`() {
        assertEquals(60..96, FluteRange.midiRange)
        assertTrue((60..96).all { FluteNotes.positionOf(it) >= 0 })
    }

    @Test
    fun `a black note sits on its letter's place`() {
        assertEquals(StaffNotes.positionOf(65), FluteNotes.positionOf(66)) // F♯ on F
        assertEquals(StaffNotes.positionOf(71), FluteNotes.positionOf(70)) // B♭ on B
        assertEquals(StaffNotes.positionOf(64), FluteNotes.positionOf(63)) // E♭ on E
        assertEquals(0, FluteNotes.positionOf(61))                         // C♯4 on c1
        assertEquals(StaffNotes.positionOf(84), FluteNotes.positionOf(84))
        assertEquals(21, FluteNotes.positionOf(96)) // c4 (C7), five ledger lines over the staff
        assertEquals(-1, FluteNotes.positionOf(97))
        assertEquals(-1, FluteNotes.positionOf(59))
    }

    // ---- The letter keys and the octave pills (2026-11-18): a key writes its letter's note in the chosen octave ----

    private fun keys(octave: Int, signed: Boolean = false) = (0..6).map { FluteNotes.inOctave(it, signed, octave) }

    @Test
    fun `a letter key writes its note in the chosen octave`() {
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71), keys(1))
        assertEquals(listOf(72, 74, 76, 77, 79, 81, 83), keys(2))
        assertEquals(listOf(84, 86, 88, 89, 91, 93, 95), keys(3))
        assertEquals("only c4 exists in octave four", listOf(96, null, null, null, null, null, null), keys(4))
    }

    @Test
    fun `with the sign a letter writes its black note in that octave, and D and A none`() {
        // C, F and G take their sharp, E and B their flat (NoteSpelling's one spelling).
        assertEquals(listOf(61, null, 63, 66, 68, null, 70), keys(1, signed = true))
        assertEquals(listOf(85, null, 87, 90, 92, null, 94), keys(3, signed = true))
        assertEquals("cis4 is past the range", List(7) { null }, keys(4, signed = true))
    }

    @Test
    fun `a letter or an octave the keys do not have writes nothing`() {
        assertNull(FluteNotes.inOctave(7, signed = false, octave = 1))
        assertNull(FluteNotes.inOctave(-1, signed = false, octave = 1))
        assertNull(FluteNotes.inOctave(0, signed = false, octave = 0))
        assertNull(FluteNotes.inOctave(0, signed = false, octave = 5))
        assertEquals(1..4, FluteNotes.OCTAVES)
    }

    @Test
    fun `a key has its name in every octave, past the range too`() {
        assertEquals(60, FluteNotes.whiteIn(0, 1))
        assertEquals(86, FluteNotes.whiteIn(1, 3))
        assertEquals("d4 has a name and no key", 98, FluteNotes.whiteIn(1, 4))
    }

    @Test
    fun `a note's octave is its letter's`() {
        assertEquals(1, FluteNotes.octaveOf(60))
        assertEquals(1, FluteNotes.octaveOf(71))
        assertEquals("B flat 4 on B's place", 1, FluteNotes.octaveOf(70))
        assertEquals(2, FluteNotes.octaveOf(72))
        assertEquals(3, FluteNotes.octaveOf(85))
        assertEquals(4, FluteNotes.octaveOf(96))
    }
}
