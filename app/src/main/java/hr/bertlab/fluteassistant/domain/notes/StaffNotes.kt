// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

/**
 * The notes the reading game asks and the places they sit on the treble staff: the
 * naturals from c1 to c3 (C4 to C6, MIDI 60 to 84), fifteen notes for the fifteen places
 * from the ledger line below the staff to the second ledger line above it. The index of a
 * note in [ALL] is its staff position, counted from the bottom.
 */
object StaffNotes {
    private const val LOWEST_MIDI = 60
    private const val HIGHEST_MIDI = 84
    private val NATURAL_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)

    /** Every note offered, lowest first. */
    val ALL: List<Int> = (LOWEST_MIDI..HIGHEST_MIDI).filter { it.mod(12) in NATURAL_PITCH_CLASSES }

    /** How many places the staff has: one per note in [ALL]. */
    const val POSITIONS = 15

    /** c1 to g1: a first lesson's worth, and where the user story starts. */
    val DEFAULT_PRACTICE: Set<Int> = setOf(60, 62, 64, 65, 67)

    /** The five staff lines, bottom (e1) to top (f2); the even positions in between are spaces. */
    val LINE_POSITIONS: List<Int> = listOf(2, 4, 6, 8, 10)

    /** The places that need no ledger line: d1, the space under the staff, to g2, the space above it. */
    val STAFF_POSITIONS: IntRange = 1..11

    /** Staff position of [midi] (0 = c1), or -1 when the note is not offered. */
    fun positionOf(midi: Int): Int = ALL.indexOf(midi)

    fun midiAt(position: Int): Int = ALL[position]

    /** The ledger lines a note at [position] needs, nearest the staff first; empty inside it. */
    fun ledgerLinesFor(position: Int): List<Int> = when {
        position < LINE_POSITIONS.first() -> (0 downTo position step 2).toList().reversed().filter { it >= 0 }
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

    /** Keeps only offered notes; a set with none left (an old build, corrupt storage) becomes the default. */
    fun sanitize(notes: Set<Int>): Set<Int> = notes.filter { it in ALL }.toSet().ifEmpty { DEFAULT_PRACTICE }
}
