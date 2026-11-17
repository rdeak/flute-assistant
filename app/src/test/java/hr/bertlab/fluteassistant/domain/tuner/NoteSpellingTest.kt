// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `every letter has its white note and five have a black one`() {
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), (0..6).map(NoteSpelling::whiteOf))
        assertEquals(listOf(1, null, 3, 6, 8, null, 10), (0..6).map(NoteSpelling::blackOf)) // C♯ E♭ F♯ G♯ B♭; D and A have none
    }

    @Test
    fun `a white note's black note is on its letter, in its octave`() {
        assertEquals(listOf(61, null, 63, 66, 68, null, 70), listOf(60, 62, 64, 65, 67, 69, 71).map(NoteSpelling::signedOf))
        assertEquals(85, NoteSpelling.signedOf(84)) // any octave: the flute's range is the flute's rule
        assertNull("a black note has none", NoteSpelling.signedOf(61))
    }

    /** The staff's places (c1 = 0, the bottom line e1 = 2), at any height: the Fingers tab's chart goes up to c4. */
    @Test
    fun `a note stands on its letter's place`() {
        assertEquals(0, NoteSpelling.placeOf(60)) // c1, a ledger line under the staff
        assertEquals(0, NoteSpelling.placeOf(61)) // cis1 on c's place
        assertEquals(2, NoteSpelling.placeOf(63)) // es1 on e's place, the bottom line
        assertEquals(2, NoteSpelling.placeOf(64))
        assertEquals(10, NoteSpelling.placeOf(77)) // f2, the top line
        assertEquals(12, NoteSpelling.placeOf(81)) // a2, the first ledger line above
        assertEquals(21, NoteSpelling.placeOf(96)) // c4
        assertEquals(-1, NoteSpelling.placeOf(59)) // h, under c1
        assertEquals(-1, NoteSpelling.placeOf(58)) // b, on h's place
    }

    /** The notes game's list is its staff: a note's index in it is its place, which `StaffNotes.midiAt` reads back. */
    @Test
    fun `the notes game's notes are the white ones, each at its place`() {
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84), StaffNotes.ALL)
        StaffNotes.ALL.forEachIndexed { index, midi -> assertEquals("midi $midi", index, NoteSpelling.placeOf(midi)) }
    }
}
