// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.Phrases
import hr.bertlab.fluteassistant.ui.metronome.TempoBand
import kotlin.math.roundToInt

/**
 * Immutable snapshot of everything the notes tab renders: one round of [ROUND_LENGTH] notes of
 * either game. In Hear the note asked is the next of [phrase] to place, so [currentMidi] is
 * `phrase[placed]`; in Find the phrase fields keep their defaults.
 */
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
    /** The game being played. */
    val game: NotesGame = NotesGame.FIND,
    /** Hear's notes, in the order they sound and sit on the staff; empty in Find. */
    val phrase: List<Int> = emptyList(),
    /** How many of [phrase] sit on the staff, the given first note included. */
    val placed: Int = 0,
    /** Changes with every phrase: the screen's key for playing it. Counts on across rounds and games. */
    val phraseId: Int = 0,
    /** True while the phrase sounds, the breath before it included; the staff takes no answer. */
    val phrasePlaying: Boolean = false,
    /**
     * The index in [phrase] of the note sounding now during a phrase's playback; null in the breath
     * before it, in the gaps between the notes and whenever no phrase plays. A placed note's own
     * sound does not set it.
     */
    val sounding: Int? = null,
    /** The staircase's length for the phrase on screen ([Phrases.MIN_LENGTH] to [Phrases.MAX_LENGTH]); the last phrase of a round may be shorter. */
    val phraseLength: Int = Phrases.MIN_LENGTH,
    /** True until a note of the phrase on screen is missed. */
    val phraseClean: Boolean = true,
    /** How well each note is known, over every round and both games. */
    val progress: NoteProgress = NoteProgress.EMPTY,
    /** Notes that moved up in [progress] this round. */
    val grewThisRound: Set<Int> = emptySet(),
    /** Notes answered this round, first try or not. */
    val askedThisRound: Set<Int> = emptySet(),
) {
    val answered: Int get() = results.size

    /** Whether the game is Hear. */
    val hearing: Boolean get() = game == NotesGame.HEAR

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
