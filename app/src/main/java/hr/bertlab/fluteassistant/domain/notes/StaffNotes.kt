// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * The notes the reading game asks and the places they sit on the treble staff: the
 * naturals from c1 to c3 (C4 to C6, MIDI 60 to 84), fifteen notes for the fifteen places
 * from the ledger line below the staff to the second ledger line above it. The index of a
 * note in [ALL] is its staff position, counted from the bottom ([NoteSpelling.placeOf]).
 */
object StaffNotes {
    private const val LOWEST_MIDI = 60
    private const val HIGHEST_MIDI = 84

    /** Every note offered, lowest first: the white ones, written with no sign. */
    val ALL: List<Int> = (LOWEST_MIDI..HIGHEST_MIDI).filter { NoteSpelling.signOf(it) == null }

    /** c1 to g1: a first lesson's worth, and where the user story starts. */
    val DEFAULT_PRACTICE: Set<Int> = setOf(60, 62, 64, 65, 67)

    /** The five staff lines, bottom (e1) to top (f2); the even positions in between are spaces. */
    val LINE_POSITIONS: List<Int> = listOf(2, 4, 6, 8, 10)

    /** The G line (g1), the second from the bottom, which the treble clef curls round. */
    const val G_LINE = 4

    /** The places that need no ledger line: d1, the space under the staff, to g2, the space above it. */
    val STAFF_POSITIONS: IntRange = 1..11

    /** Staff position of [midi] (0 = c1), or -1 when the note is not offered. */
    fun positionOf(midi: Int): Int = ALL.indexOf(midi)

    fun midiAt(position: Int): Int = ALL[position]

    /** The ledger lines a note at [position] needs, nearest the staff first; empty inside it. */
    fun ledgerLinesFor(position: Int): List<Int> = when {
        position <= 0 -> listOf(0) // c1's own line; d1, the space over it, needs none
        position > LINE_POSITIONS.last() -> (LINE_POSITIONS.last() + 2..position step 2).toList()
        else -> emptyList()
    }

    /**
     * The places the staff shows for [practice]: always [STAFF_POSITIONS], stretched down to c1
     * or up through the ledger-line notes only as far as a chosen note needs, so a child who has
     * not met those notes never sees their ledger lines.
     */
    fun visiblePositions(practice: Set<Int>): IntRange {
        val chosen = practice.map(::positionOf).filter { it >= 0 }
        val lowest = minOf(STAFF_POSITIONS.first, chosen.minOrNull() ?: STAFF_POSITIONS.first)
        val highest = maxOf(STAFF_POSITIONS.last, chosen.maxOrNull() ?: STAFF_POSITIONS.last)
        return lowest..highest
    }

    /**
     * [practice] with [midi] switched in the picker: added, or taken out unless it is the last note left. Only an
     * offered note switches; a switch that is refused gives [practice] itself.
     */
    fun toggled(practice: Set<Int>, midi: Int): Set<Int> {
        if (midi !in ALL) return practice
        val switched = if (midi in practice) practice - midi else practice + midi
        return switched.ifEmpty { practice }
    }

    /** Keeps only offered notes; a set with none left (an old build, corrupt storage) becomes the default. */
    fun sanitize(notes: Set<Int>): Set<Int> = notes.filter { it in ALL }.toSet().ifEmpty { DEFAULT_PRACTICE }
}
