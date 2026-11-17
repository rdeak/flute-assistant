// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * The flute's own notes (since 2026-11-16): every semitone from C4 to C6, the white notes and the black ones between
 * them (C♯ E♭ F♯ G♯ B♭, spelled by [NoteSpelling]). The keys are the white ones, and the ♯♭ key makes one write its
 * black note ([signed], since 2026-11-17). The notes game keeps its fifteen white notes ([StaffNotes]); the flute's
 * tune and its staff use this list. A black note sits on its letter's staff place.
 */
object FluteNotes {
    const val LOWEST = 60
    const val HIGHEST = 84

    /** Every note the keys offer, lowest first. */
    val ALL: List<Int> = (LOWEST..HIGHEST).toList()

    /** The staff place of [midi] (0 = c¹, as [StaffNotes.positionOf]): its letter's; −1 when the flute has no such key. */
    fun positionOf(midi: Int): Int = if (midi in LOWEST..HIGHEST) StaffNotes.positionOf(NoteSpelling.naturalOf(midi)) else -1

    /**
     * The black note the white key [key] writes under the ♯♭ key (since 2026-11-17): the one the app spells on that key's
     * letter ([NoteSpelling]), so C, F and G take their sharp and E and B their flat; null for D and A, and for C6, whose
     * sharp is past the flute's range.
     */
    fun signed(key: Int): Int? = listOf(key + 1, key - 1).firstOrNull {
        it in LOWEST..HIGHEST && NoteSpelling.signOf(it) != null && NoteSpelling.naturalOf(it) == key
    }
}
