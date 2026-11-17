// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

/**
 * An arc over joined notes ([Joins.arcs]): a slur from step [from] to step [to] of the tune (the first and the last
 * note under it), or, with [tie], a tie between two steps of the same note.
 */
data class JoinArc(val from: Int, val to: Int, val tie: Boolean)

/**
 * Runs of joined notes (since 2026-11-16, the ⌒ key: a [TuneNote.joined] note is joined to the note before it). The
 * staff draws them ([arcs]); Listen to me hears a tie, or a slur to the same letter in another octave, as one note
 * ([heardWithBefore], [lastHeardWith]: it judges a letter in any octave, so it cannot hear that step), and judges
 * whether a note joined to another letter was tongued ([joinsOtherLetter]). Pure Kotlin.
 */
object Joins {

    /**
     * Every arc the staff draws over [tune], ordered by [JoinArc.from]: for each run of joined notes with at least one
     * join between two different notes, one slur from its first note to its last, and for each joined pair of the same
     * note, a tie (after the slur that starts at the same note).
     */
    fun arcs(tune: List<TuneItem>): List<JoinArc> {
        val arcs = mutableListOf<JoinArc>()
        var start = 0
        while (start < tune.size) {
            if (tune[start] !is TuneNote) {
                start++
                continue
            }
            var end = start
            while ((tune.getOrNull(end + 1) as? TuneNote)?.joined == true) end++
            val notes = (start..end).map { (tune[it] as TuneNote).midi }
            if (notes.zipWithNext().any { (a, b) -> a != b }) arcs += JoinArc(start, end, tie = false)
            for (index in start + 1..end) {
                if (notes[index - start] == notes[index - start - 1]) arcs += JoinArc(index - 1, index, tie = true)
            }
            start = end + 1
        }
        return arcs
    }

    /** Whether the ⌒ key has a note to join the next one to: the last step is a note, and the tune is not at the cap. */
    fun canJoinAfter(tune: List<TuneItem>): Boolean = tune.lastOrNull() is TuneNote && tune.size < Tune.MAX_NOTES

    /** Whether the note at [index] is joined to a note of the same letter before it, in any octave: a tie, or an octave slur. */
    fun heardWithBefore(tune: List<TuneItem>, index: Int): Boolean = joinedInLetter(tune, index) == true

    /** The last step of the notes heard as one that start at [head]: [head] itself when none is joined on in its letter. */
    fun lastHeardWith(tune: List<TuneItem>, head: Int): Int {
        var end = head
        while (heardWithBefore(tune, end + 1)) end++
        return end
    }

    /**
     * Where a walk [halfBeat] half beats into the notes heard as one from [head] stands: the step among them it has
     * reached, and the half beats into that step. Listen to me walks a tie or an octave slur as one note, counted from
     * its first note's head ([lastHeardWith]); the staff draws the animal on the note it is over. Past the last of
     * them it stays on the last, however far.
     */
    fun placeOf(tune: List<TuneItem>, head: Int, halfBeat: Int): Pair<Int, Int> {
        var index = head
        var left = halfBeat
        while (heardWithBefore(tune, index + 1)) {
            val length = tune[index].length?.halfBeats ?: 0
            if (left < length) break
            left -= length
            index++
        }
        return index to left
    }

    /** Whether the note at [index] is joined to a note of another letter before it: a slur Listen to me can hear. */
    fun joinsOtherLetter(tune: List<TuneItem>, index: Int): Boolean = joinedInLetter(tune, index) == false

    /** Whether the note at [index], joined to the note before it, has that note's letter in any octave; null when it is not a note joined to a note. */
    private fun joinedInLetter(tune: List<TuneItem>, index: Int): Boolean? {
        val note = tune.getOrNull(index) as? TuneNote ?: return null
        val before = tune.getOrNull(index - 1) as? TuneNote ?: return null
        return if (note.joined) TuneCheck.sameLetter(note.midi, before.midi) else null
    }
}
