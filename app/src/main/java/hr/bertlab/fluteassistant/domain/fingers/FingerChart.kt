// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.fingers

import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * What the Fingers tab lists for a note (spec `2026-11-17-fingers-tab-design.md`, part 3): every octave of a pitch
 * class the flute plays, c1 to c4. Each is written on its letter's staff place ([NoteSpelling.placeOf], the notes
 * game's places carried up to c4), with the ledger lines `StaffNotes.ledgerLinesFor` gives at any place. Pure Kotlin.
 */
object FingerChart {
    /** The notes of each pitch class, worked out once: the range and the twelve classes never change. */
    private val NOTES: List<List<Int>> = List(12) { pitchClass -> FluteRange.midiRange.filter { NoteSpelling.pitchClassOf(it) == pitchClass } }

    /** The notes of [pitchClass] (0 = C to 11 = B) the flute plays, low to high: four for C, three for the rest. */
    fun notesOf(pitchClass: Int): List<Int> = NOTES[NoteSpelling.pitchClassOf(pitchClass)]

    /** The most notes a pitch class has, C's four (c1 to c4): the Fingers tab sizes a row for this many, whatever the note. */
    val MOST_NOTES: Int = NOTES.maxOf { it.size }
}
