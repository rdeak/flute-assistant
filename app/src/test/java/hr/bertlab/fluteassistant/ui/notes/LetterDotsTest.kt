// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import org.junit.Assert.assertEquals
import org.junit.Test

class LetterDotsTest {

    private val c = NoteLetterColors[0]
    private val d = NoteLetterColors[1]

    @Test
    fun `a letter's dot is known only when every chosen note of it is`() {
        // C4 and C5 are both C, so D4 alone makes the second dot.
        val chosen = setOf(60, 72, 62)

        assertEquals(
            listOf(c to true, d to false),
            letterDotColors(chosen, NoteProgress(mapOf(60 to 8, 72 to 8))),
        )
        assertEquals(
            listOf(c to false, d to false),
            letterDotColors(chosen, NoteProgress(mapOf(60 to 8))),
        )
    }
}
