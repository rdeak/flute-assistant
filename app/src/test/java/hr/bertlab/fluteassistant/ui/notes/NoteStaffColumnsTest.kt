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
}
