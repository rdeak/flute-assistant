// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * The flute's own notes: every semitone from C4 to C7 (c¹ to c⁴, since 2026-11-18; C4 to C6 from 2026-11-16), the
 * tuner's and the Fingers tab's range ([FluteRange]), the black ones spelled by [NoteSpelling] (C♯ E♭ F♯ G♯ B♭). The
 * keys are seven letters and the four octaves as pills over them (since 2026-11-18; spec
 * `2026-11-18-flute-every-octave-design.md`, Revision 3): a letter writes its note in the chosen octave ([inOctave]),
 * its black note while the ♯♭ key waits. The notes game keeps its fifteen white notes ([StaffNotes]); the flute's tune
 * and its staff use this list. A black note sits on its letter's staff place.
 */
object FluteNotes {
    private const val LOWEST = FluteRange.LOWEST_MIDI

    /** The octaves the pills offer: 1 (c¹ to h¹) to 4, where only c⁴ is a note of the flute. */
    val OCTAVES: IntRange = 1..4

    /** The staff place of [midi] (0 = c¹, [NoteSpelling.placeOf]): its letter's; −1 when the flute has no such note. */
    fun positionOf(midi: Int): Int = if (midi in FluteRange.midiRange) NoteSpelling.placeOf(midi) else -1

    /**
     * The white note of the letter key [letter] (0 = C to 6 = B) in [octave], whether the flute has it or not: the name
     * a key shows (d⁴ has a name and writes nothing).
     */
    fun whiteIn(letter: Int, octave: Int): Int = baseOf(octave) + NoteSpelling.whiteOf(letter)

    /**
     * The note the letter key [letter] writes in [octave]: its white note, or while the ♯♭ key waits ([signed]) its black
     * note in the app's one spelling ([NoteSpelling.blackOf]: none for D and A). Null when it has none, when the note is
     * past the flute's range (all of octave 4 but c⁴), and for a letter or an octave the keys do not have.
     */
    fun inOctave(letter: Int, signed: Boolean, octave: Int): Int? {
        if (letter !in 0..6 || octave !in OCTAVES) return null
        val pitchClass = if (signed) NoteSpelling.blackOf(letter) ?: return null else NoteSpelling.whiteOf(letter)
        return (baseOf(octave) + pitchClass).takeIf { it in FluteRange.midiRange }
    }

    /** The octave [midi] is written in, by its letter (B♭ with B): 1 for c¹ to h¹, 4 for c⁴. */
    fun octaveOf(midi: Int): Int = (NoteSpelling.naturalOf(midi) - LOWEST).floorDiv(12) + 1

    /** The C [octave] starts on: c¹ for 1. */
    private fun baseOf(octave: Int): Int = LOWEST + 12 * (octave - 1)
}
