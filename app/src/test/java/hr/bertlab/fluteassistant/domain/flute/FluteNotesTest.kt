// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import org.junit.Assert.assertEquals
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
    fun `an octave's white keys and its black keys, each after the white key it follows`() {
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71), FluteNotes.whitesOf(60))
        assertEquals(listOf(61 to 0, 63 to 1, 66 to 3, 68 to 4, 70 to 5), FluteNotes.blacksOf(60))
        assertEquals(listOf(73 to 0, 75 to 1, 78 to 3, 80 to 4, 82 to 5), FluteNotes.blacksOf(72))
    }
}
