// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

/** The sign a written note carries before it: a sharp, a flat, or a natural that cancels one earlier in its bar. */
enum class Accidental { SHARP, FLAT, NATURAL }

/**
 * One spelling for the whole app (since 2026-11-16, sharps and flats on the flute): every note is written on a letter
 * (C = 0 to B = 6), and a black note carries a sign, as beginners' flute music writes them: C♯, E♭, F♯, G♯ and B♭. A
 * sharp sits on the letter below, a flat on the letter above. The names (`note_names`), the colours (`noteColor`, the
 * letter's) and the staff place (the letter's) all follow it. Pure Kotlin.
 */
object NoteSpelling {
    private val LETTER = intArrayOf(0, 0, 1, 2, 2, 3, 3, 4, 4, 5, 6, 6)
    private val SIGN = arrayOf(null, Accidental.SHARP, null, Accidental.FLAT, null, null, Accidental.SHARP, null, Accidental.SHARP, null, Accidental.FLAT, null)

    /** The letter [midi] is written on: C = 0, D = 1, … B = 6. */
    fun letterOf(midi: Int): Int = LETTER[Math.floorMod(midi, 12)]

    /** The sign [midi] is written with: [Accidental.SHARP] or [Accidental.FLAT] for a black note, null for a white one. */
    fun signOf(midi: Int): Accidental? = SIGN[Math.floorMod(midi, 12)]

    /** The white note on [midi]'s letter, in its octave: E♭4 (63) is on E4 (64); a white note is its own. */
    fun naturalOf(midi: Int): Int = when (signOf(midi)) {
        Accidental.SHARP -> midi - 1 // a sharp sits on the letter below
        Accidental.FLAT -> midi + 1 // a flat on the letter above
        else -> midi
    }
}
