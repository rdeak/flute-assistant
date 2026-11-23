// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffNotesTest {

    @Test
    fun `all is the fifteen naturals from c1 to c3`() {
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84), StaffNotes.ALL)
        assertEquals(15, StaffNotes.ALL.size)
    }

    @Test
    fun `position is the index on the staff`() {
        assertEquals(0, StaffNotes.positionOf(60))
        assertEquals(2, StaffNotes.positionOf(64))
        assertEquals(14, StaffNotes.positionOf(84))
        assertEquals(-1, StaffNotes.positionOf(61))
        assertEquals(-1, StaffNotes.positionOf(96))
        assertEquals(67, StaffNotes.midiAt(4))
    }

    @Test
    fun `lines and ledger lines`() {
        assertEquals(listOf(2, 4, 6, 8, 10), StaffNotes.LINE_POSITIONS)
        assertEquals(listOf(0), StaffNotes.ledgerLinesFor(0))
        assertTrue(StaffNotes.ledgerLinesFor(1).isEmpty())
        assertTrue(StaffNotes.ledgerLinesFor(6).isEmpty())
        assertEquals(listOf(12), StaffNotes.ledgerLinesFor(12))
        assertEquals(listOf(12), StaffNotes.ledgerLinesFor(13))
        assertEquals(listOf(12, 14), StaffNotes.ledgerLinesFor(14))
        // Past the game's notes too: the Fingers tab writes every note up to c4 on this staff.
        assertEquals(listOf(12, 14, 16, 18, 20), StaffNotes.ledgerLinesFor(NoteSpelling.placeOf(96)))
    }

    @Test
    fun `visible places stretch from the staff to the chosen ledger notes`() {
        assertEquals(1..11, StaffNotes.STAFF_POSITIONS) // d1 to g2 need no ledger line
        assertEquals(0..11, StaffNotes.visiblePositions(StaffNotes.DEFAULT_PRACTICE)) // c1 is chosen
        assertEquals(1..11, StaffNotes.visiblePositions(setOf(62, 64)))
        assertEquals(1..14, StaffNotes.visiblePositions(setOf(84)))
        assertEquals(1..13, StaffNotes.visiblePositions(setOf(83)))
        assertEquals(0..14, StaffNotes.visiblePositions(setOf(60, 84)))
        assertEquals(1..11, StaffNotes.visiblePositions(setOf(61, 96))) // foreign notes count for nothing
    }

    @Test
    fun `a note of the picker switches in and out, but the last one stays and a foreign one does nothing`() {
        assertEquals(setOf(60, 62), StaffNotes.toggled(setOf(60), 62))
        assertEquals(setOf(60), StaffNotes.toggled(setOf(60, 62), 62))
        val last = setOf(60)
        assertSame("the last note stays", last, StaffNotes.toggled(last, 60))
        assertSame("a black note is not offered", last, StaffNotes.toggled(last, 61))
    }

    @Test
    fun `sanitize drops foreign notes and never returns empty`() {
        assertEquals(setOf(60), StaffNotes.sanitize(setOf(60, 61, 96)))
        assertEquals(StaffNotes.DEFAULT_PRACTICE, StaffNotes.sanitize(emptySet()))
        assertEquals(StaffNotes.DEFAULT_PRACTICE, StaffNotes.sanitize(setOf(61)))
        assertEquals(setOf(60, 62, 64, 65, 67), StaffNotes.DEFAULT_PRACTICE)
    }
}
