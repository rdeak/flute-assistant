// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import kotlin.math.abs
import kotlin.random.Random

/**
 * The phrases the Hear game plays: how one is drawn from the chosen notes, and how long the
 * next one is. A phrase moves by steps and thirds, because that is how a first tune moves, and
 * it grows one note after a clean phrase and shrinks one after a miss.
 */
object Phrases {
    /** The shortest phrase: the given note and one to place. */
    const val MIN_LENGTH = 2

    /** The longest phrase. */
    const val MAX_LENGTH = 4

    /** The furthest a note may sit from the one before it, in staff places: 2 is a third. */
    const val REACH = 2

    /** The length of the phrase after one: a place longer when it was [clean], one shorter otherwise. */
    fun nextLength(current: Int, clean: Boolean): Int =
        (if (clean) current + 1 else current - 1).coerceIn(MIN_LENGTH, MAX_LENGTH)

    /**
     * The length of a phrase that [target] asks for when [answeredLeft] notes of the round are
     * still to be answered: the given note comes free, so the last phrase is one longer than
     * what is left. The caller never asks with fewer than one left.
     */
    fun lengthFor(target: Int, answeredLeft: Int): Int =
        minOf(target, answeredLeft + 1).coerceAtLeast(MIN_LENGTH)

    /**
     * A phrase of [length] notes from [notes] (offered staff notes, never empty). The first note
     * is any of them but [avoidFirst], unless that leaves none. Each next note is within 1 to
     * [REACH] staff places of the one before; when none is that close it is any other note, and
     * when there is no other note it is the one before again. Candidates are sorted before
     * [random] picks, so a seed always gives the same phrase.
     */
    fun draw(notes: Set<Int>, length: Int, random: Random, avoidFirst: Int? = null): List<Int> {
        val first = pick((notes - setOfNotNull(avoidFirst)).ifEmpty { notes }, random)
        return buildList {
            add(first)
            while (size < length) add(next(notes, last(), random))
        }
    }

    private fun next(notes: Set<Int>, previous: Int, random: Random): Int {
        val here = StaffNotes.positionOf(previous)
        val near = notes.filter { abs(StaffNotes.positionOf(it) - here) in 1..REACH }
        if (near.isNotEmpty()) return pick(near, random)
        val others = notes - previous
        return if (others.isNotEmpty()) pick(others, random) else previous
    }

    private fun pick(candidates: Collection<Int>, random: Random): Int {
        val sorted = candidates.sorted()
        return sorted[random.nextInt(sorted.size)]
    }
}
