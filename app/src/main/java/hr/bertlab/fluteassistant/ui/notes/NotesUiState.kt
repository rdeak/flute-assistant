// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.ui.metronome.TempoBand
import kotlin.math.roundToInt

/** Immutable snapshot of everything the note reading game renders: one round of [ROUND_LENGTH] notes. */
data class NotesUiState(
    /** The notes the child is practising, MIDI numbers from StaffNotes.ALL; never empty. */
    val practiceNotes: Set<Int>,
    /** The note being asked. */
    val currentMidi: Int,
    /** Notes tapped by mistake for the current question; each marked with a cross until it is answered. */
    val wrongTaps: Set<Int> = emptySet(),
    /** The latest of [wrongTaps]: its cross gets the arrow pointing towards the right place. */
    val lastWrongTap: Int? = null,
    /** True from the right tap until the next note appears: the place is green and the note sounds. */
    val solved: Boolean = false,
    /** One entry per note answered this round: true when found on the first tap. */
    val results: List<Boolean> = emptyList(),
    /** Notes found on the first tap in a row, right now. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Every tap this round, right or wrong, for the accuracy. */
    val taps: Int = 0,
    /** True once [ROUND_LENGTH] notes are answered and the summary is up. */
    val roundOver: Boolean = false,
    /** Whether a found note is sounded; a toggle beside the note picker, remembered. */
    val soundOn: Boolean = true,
    /** How long the round took, first tap to the tenth note found; null until then. */
    val roundMillis: Long? = null,
) {
    val answered: Int get() = results.size

    /** Notes found on the first tap. */
    val score: Int get() = results.count { it }

    /** Right taps out of all taps, in whole percent (every answered note took exactly one right tap); null before the first tap. */
    val accuracyPercent: Int? get() = if (taps == 0) null else (results.size * 100.0 / taps).roundToInt()

    companion object {
        const val ROUND_LENGTH = 10
    }
}

/**
 * How fast a round went, as one of the metronome's animals ([TempoBand]) rather than a time:
 * the average time per note, the cheetah for under two seconds down to the snail for over
 * ten. Null until the round is over.
 */
fun NotesUiState.speed(): TempoBand? {
    val perNote = (roundMillis ?: return null) / NotesUiState.ROUND_LENGTH
    return when {
        perNote <= 2_000 -> TempoBand.CHEETAH
        perNote <= 3_500 -> TempoBand.RABBIT
        perNote <= 6_000 -> TempoBand.DUCK
        perNote <= 10_000 -> TempoBand.TURTLE
        else -> TempoBand.SNAIL
    }
}

/**
 * How well the child aimed, one to three stars from [NotesUiState.accuracyPercent]: three
 * from 90 %, two from 70 %, one otherwise (they found every note in the end).
 */
fun NotesUiState.aimStars(): Int {
    val percent = accuracyPercent ?: return 1
    return when {
        percent >= 90 -> 3
        percent >= 70 -> 2
        else -> 1
    }
}
