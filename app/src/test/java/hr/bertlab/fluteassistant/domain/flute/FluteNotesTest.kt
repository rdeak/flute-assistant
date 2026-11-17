// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The flute's own notes (since 2026-11-16): every semitone from C4 to C6, a black note on its letter's place. */
class FluteNotesTest {

    @Test
    fun `the flute has every semitone from c4 to c6`() {
        assertEquals((60..84).toList(), FluteNotes.ALL)
        assertEquals(25, FluteNotes.ALL.size)
    }

    @Test
    fun `a black note sits on its letter's place`() {
        assertEquals(StaffNotes.positionOf(65), FluteNotes.positionOf(66)) // F♯ on F
        assertEquals(StaffNotes.positionOf(71), FluteNotes.positionOf(70)) // B♭ on B
        assertEquals(StaffNotes.positionOf(64), FluteNotes.positionOf(63)) // E♭ on E
        assertEquals(0, FluteNotes.positionOf(61))                         // C♯4 on c1
        assertEquals(StaffNotes.positionOf(84), FluteNotes.positionOf(84))
        assertEquals(-1, FluteNotes.positionOf(85))
        assertEquals(-1, FluteNotes.positionOf(59))
    }

    @Test
    fun `each white key's signed note is the one the app spells on its letter`() {
        // The ♯♭ key (2026-11-17): C, F and G take their sharp, E and B their flat; D, A and C6 have none.
        val signed = mapOf(60 to 61, 64 to 63, 65 to 66, 67 to 68, 71 to 70, 72 to 73, 76 to 75, 77 to 78, 79 to 80, 83 to 82)
        for ((key, note) in signed) assertEquals("$key", note, FluteNotes.signed(key))
        for (key in listOf(62, 69, 74, 81, 84)) assertNull("$key", FluteNotes.signed(key))
    }
}
