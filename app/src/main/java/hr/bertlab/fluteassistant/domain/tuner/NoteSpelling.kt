// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

/** The sign a written note carries before it: a sharp, a flat, or a natural that cancels one earlier in its bar. */
enum class Accidental { SHARP, FLAT, NATURAL }

/**
 * One spelling for the whole app (since 2026-11-16, sharps and flats on the flute): every note is written on a letter
 * (C = 0 to B = 6), and a black note carries a sign, as beginners' flute music writes them: C♯, E♭, F♯, G♯ and B♭. A
 * sharp sits on the letter below, a flat on the letter above. The names (`note_names`), the colours (`noteColor`, the
 * letter's) and the staff place (the letter's, [placeOf]) all follow it, and so does which black note a letter's key
 * writes under a ♯♭ key ([blackOf]: the flute's and the Fingers tab's). Pure Kotlin.
 */
object NoteSpelling {
    private val LETTER = intArrayOf(0, 0, 1, 2, 2, 3, 3, 4, 4, 5, 6, 6)
    private val SIGN = arrayOf(null, Accidental.SHARP, null, Accidental.FLAT, null, null, Accidental.SHARP, null, Accidental.SHARP, null, Accidental.FLAT, null)

    /** Each letter's white and black pitch class, read once from the two tables above: there is no third to keep in step. */
    private val WHITE = IntArray(7) { letter -> (0..11).first { LETTER[it] == letter && SIGN[it] == null } }
    private val BLACK = arrayOfNulls<Int>(7).also { black -> for (pc in 0..11) if (SIGN[pc] != null) black[LETTER[pc]] = pc }

    /** Middle C, c1: where the staff's places start ([placeOf]). */
    private const val MIDDLE_C = 60

    /** The pitch class of [midi]: the note with no octave, C = 0 to B = 11. */
    fun pitchClassOf(midi: Int): Int = midi.mod(12)

    /** The letter [midi] is written on: C = 0, D = 1, … B = 6. */
    fun letterOf(midi: Int): Int = LETTER[pitchClassOf(midi)]

    /** The sign [midi] is written with: [Accidental.SHARP] or [Accidental.FLAT] for a black note, null for a white one. */
    fun signOf(midi: Int): Accidental? = SIGN[pitchClassOf(midi)]

    /** The white note on [midi]'s letter, in its octave: E♭4 (63) is on E4 (64); a white note is its own. */
    fun naturalOf(midi: Int): Int = when (signOf(midi)) {
        Accidental.SHARP -> midi - 1 // a sharp sits on the letter below
        Accidental.FLAT -> midi + 1 // a flat on the letter above
        Accidental.NATURAL, null -> midi
    }

    /** The pitch class of [letter]'s white note (C = 0 to B = 6): 0, 2, 4, 5, 7, 9, 11. */
    fun whiteOf(letter: Int): Int = WHITE[letter]

    /** The pitch class of the black note written on [letter] with a sign (C♯ E♭ F♯ G♯ B♭), or null for D and A, which have none. */
    fun blackOf(letter: Int): Int? = BLACK[letter]

    /**
     * Where [midi] is written on the treble staff, one place a line or a space, on its letter: c1 (MIDI 60) is 0, on
     * a ledger line under the staff; the bottom line e1 is 2; f2 is 10, the top line; c4 is 21. The notes game's
     * places, the flute's staff and the Fingers tab's chart all count them so.
     */
    fun placeOf(midi: Int): Int = letterOf(midi) + 7 * (naturalOf(midi) - MIDDLE_C).floorDiv(12)
}
