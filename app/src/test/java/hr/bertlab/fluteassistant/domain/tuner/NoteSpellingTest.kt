// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

/** One spelling for the whole app (since 2026-11-16): each note's letter and sign, as beginners' flute music writes them. */
class NoteSpellingTest {

    private val letters = listOf(0, 0, 1, 2, 2, 3, 3, 4, 4, 5, 6, 6)
    private val signs = listOf(null, Accidental.SHARP, null, Accidental.FLAT, null, null, Accidental.SHARP, null, Accidental.SHARP, null, Accidental.FLAT, null)
    private val naturals = listOf(0, 0, 2, 4, 4, 5, 5, 7, 7, 9, 11, 11)

    @Test
    fun `every pitch class has its letter, a sharp on the letter below and a flat on the letter above`() {
        for (octave in listOf(60, 72)) {
            for (pc in 0 until 12) {
                assertEquals("letter of ${octave + pc}", letters[pc], NoteSpelling.letterOf(octave + pc))
                assertEquals("sign of ${octave + pc}", signs[pc], NoteSpelling.signOf(octave + pc))
                assertEquals("natural of ${octave + pc}", octave + naturals[pc], NoteSpelling.naturalOf(octave + pc))
            }
        }
    }

    @Test
    fun `the black keys are c sharp, e flat, f sharp, g sharp and b flat`() {
        assertEquals(listOf(60, 64, 65, 67, 71), listOf(61, 63, 66, 68, 70).map(NoteSpelling::naturalOf))
        assertEquals(84, NoteSpelling.naturalOf(84))
    }
}
