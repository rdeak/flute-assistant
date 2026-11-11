// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteStaffColumnsTest {

    private fun assertColumns(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) -> assertEquals(want, got, 1e-4f) }
    }

    @Test
    fun `columns spread around the middle sixteen hundredths apart`() {
        assertEquals(listOf(0.5f), phraseColumns(1))
        assertColumns(listOf(0.42f, 0.58f), phraseColumns(2))
        assertColumns(listOf(0.34f, 0.5f, 0.66f), phraseColumns(3))
        assertColumns(listOf(0.26f, 0.42f, 0.58f, 0.74f), phraseColumns(4))
        assertEquals(listOf(0.5f), phraseColumns(0))
    }

    /** Find is a phrase of one with nothing given: its one column has a slot until its note is found. */
    @Test
    fun `find has one slot until its note is found`() {
        assertEquals(listOf(0), slotIndices(phraseSize = 0, placed = 0, solved = false))
        assertEquals(emptyList<Int>(), slotIndices(phraseSize = 0, placed = 0, solved = true))
    }

    @Test
    fun `hear has a slot for every note still to find`() {
        assertEquals(listOf(1, 2, 3), slotIndices(phraseSize = 4, placed = 1, solved = false))
        assertEquals(listOf(2, 3), slotIndices(phraseSize = 4, placed = 1, solved = true))
        assertEquals(emptyList<Int>(), slotIndices(phraseSize = 2, placed = 1, solved = true))
    }
}
