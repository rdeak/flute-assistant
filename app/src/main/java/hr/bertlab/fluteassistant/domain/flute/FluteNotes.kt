// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * The flute's own notes (since 2026-11-16): every semitone from C4 to C6, the white keys and the black keys between
 * them (C♯ E♭ F♯ G♯ B♭, spelled by [NoteSpelling]). The notes game keeps its fifteen white notes ([StaffNotes]); the
 * flute's keys, its tune and its staff use this list. A black note sits on its letter's staff place.
 */
object FluteNotes {
    const val LOWEST = 60
    const val HIGHEST = 84

    /** Every note the keys offer, lowest first. */
    val ALL: List<Int> = (LOWEST..HIGHEST).toList()

    /** The staff place of [midi] (0 = c¹, as [StaffNotes.positionOf]): its letter's; −1 when the flute has no such key. */
    fun positionOf(midi: Int): Int = if (midi in LOWEST..HIGHEST) StaffNotes.positionOf(NoteSpelling.naturalOf(midi)) else -1

    /** The seven white keys of the octave from [c] (a C), lowest first. */
    fun whitesOf(c: Int): List<Int> = (c until c + 12).filter { NoteSpelling.signOf(it) == null }

    /** The five black keys of the octave from [c], each with the index of the white key it stands after (C♯ after C, 0). */
    fun blacksOf(c: Int): List<Pair<Int, Int>> = whitesOf(c).let { whites ->
        (c until c + 12).filter { NoteSpelling.signOf(it) != null }.map { black -> black to whites.indexOf(black - 1) }
    }
}
