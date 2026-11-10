// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * One round of the notes game, [ROUND_LENGTH] answered notes of either game, and everything the
 * notes tab renders of it. Find asks a note and judges each tap on the staff; Hear plays a short
 * phrase and asks for it back, note by note: there the note asked is the next of [phrase] to
 * place, so [currentMidi] is `phrase[placed]`; in Find the phrase fields keep their defaults.
 *
 * Immutable, and the rules are its own: [open] starts a round, [tapped] judges a tap and [next]
 * puts up the next note or phrase, each returning the round that follows. The view model keeps
 * the current one and does what a round cannot: the sound, the waits, the storage and the log.
 */
data class NotesRound(
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
    /** When the round's first tap happened, on the clock [tapped] is given, so the round can say how long it took; null before it. */
    val startedAt: Long? = null,
) {
    val answered: Int get() = results.size

    /** Whether the game is Hear. */
    val hearing: Boolean get() = game == NotesGame.HEAR

    /** Notes found on the first tap. */
    val score: Int get() = results.count { it }

    /** Right taps out of all taps, in whole percent (every answered note took exactly one right tap); null before the first tap. */
    val accuracyPercent: Int? get() = if (taps == 0) null else (results.size * 100.0 / taps).roundToInt()

    /**
     * The round after the child tapped the place of [midi] on the staff at [now]. The same round
     * (nothing happened) while the last answer still shows, a phrase plays or the round is over.
     *
     * The right place scores the note: found on the first tap it counts for the streak and moves
     * the note up in the sticker book ([progress]). Find then shows the answer ([solved]) until
     * [next]; Hear does so only after the phrase's last note, the next column being live at once
     * until then. A wrong place gets a cross and ends the streak; only the first miss for a note
     * costs it a step in the book, and in Hear it spoils the phrase for the staircase
     * ([phraseClean]).
     */
    fun tapped(midi: Int, now: Long): NotesRound {
        if (solved || roundOver || phrasePlaying) return this
        val started = startedAt ?: now
        if (midi != currentMidi) {
            return copy(
                wrongTaps = wrongTaps + midi,
                lastWrongTap = midi,
                taps = taps + 1,
                streak = 0,
                phraseClean = phraseClean && !hearing,
                progress = if (wrongTaps.isEmpty()) progress.missed(currentMidi) else progress,
                startedAt = started,
            )
        }
        val firstTry = wrongTaps.isEmpty()
        val newStreak = if (firstTry) streak + 1 else 0
        val newProgress = if (firstTry) progress.found(midi) else progress
        val scored = copy(
            taps = taps + 1,
            results = results + firstTry,
            streak = newStreak,
            bestStreak = max(bestStreak, newStreak),
            progress = newProgress,
            grewThisRound = if (newProgress.countOf(midi) > progress.countOf(midi)) grewThisRound + midi else grewThisRound,
            askedThisRound = askedThisRound + midi,
            startedAt = started,
        )
        // Find ends with every note; Hear only with the phrase's last, whose playback is the feedback.
        val lastNote = !hearing || placed == phrase.size - 1
        return if (lastNote) {
            scored.copy(solved = true, roundMillis = if (scored.answered >= ROUND_LENGTH) now - started else roundMillis)
        } else {
            scored.copy(placed = placed + 1, currentMidi = phrase[placed + 1], wrongTaps = emptySet(), lastWrongTap = null)
        }
    }

    /**
     * The round after the answer just given has been shown: over after the tenth note ([roundOver]),
     * else with the next note (Find, never the one just asked) or the next phrase (Hear: a note
     * longer after a clean phrase, one shorter after a miss, never opening on the same note),
     * drawn with [random].
     */
    fun next(random: Random): NotesRound {
        val cleared = copy(solved = false, wrongTaps = emptySet(), lastWrongTap = null)
        return when {
            answered >= ROUND_LENGTH -> cleared.copy(phrasePlaying = false, sounding = null, roundOver = true)
            hearing -> {
                val length = Phrases.nextLength(phraseLength, phraseClean)
                val drawn = Phrases.draw(
                    practiceNotes,
                    Phrases.lengthFor(length, answeredLeft = ROUND_LENGTH - answered),
                    random,
                    avoidFirst = phrase.first(),
                )
                cleared.copy(
                    phrasePlaying = false,
                    sounding = null,
                    phrase = drawn,
                    placed = 1,
                    currentMidi = drawn[1],
                    phraseId = phraseId + 1,
                    phraseLength = length,
                    phraseClean = true,
                )
            }
            else -> cleared.copy(currentMidi = draw(practiceNotes, avoid = currentMidi, random))
        }
    }

    companion object {
        const val ROUND_LENGTH = 10

        /**
         * A fresh round of [game] over [notes]. [previous] is the round it replaces, null at the
         * start: the new one does not open with its note, or its phrase's first note, again, and
         * the phrase counter goes on from it. Hear opens with two notes, the first given.
         */
        fun open(
            notes: Set<Int>,
            game: NotesGame,
            soundOn: Boolean,
            progress: NoteProgress,
            random: Random,
            previous: NotesRound?,
        ): NotesRound {
            val phraseId = previous?.phraseId ?: 0
            if (game == NotesGame.FIND) {
                return NotesRound(
                    practiceNotes = notes,
                    currentMidi = draw(notes, avoid = previous?.currentMidi, random),
                    soundOn = soundOn,
                    phraseId = phraseId,
                    progress = progress,
                )
            }
            val phrase = Phrases.draw(notes, Phrases.MIN_LENGTH, random, avoidFirst = previous?.phrase?.firstOrNull())
            return NotesRound(
                practiceNotes = notes,
                currentMidi = phrase[1],
                soundOn = soundOn,
                game = game,
                phrase = phrase,
                placed = 1,
                phraseId = phraseId + 1,
                progress = progress,
            )
        }

        /** A random note from [notes], not [avoid] unless it is the only one. */
        private fun draw(notes: Set<Int>, avoid: Int?, random: Random): Int {
            val candidates = (notes - setOfNotNull(avoid)).ifEmpty { notes }.sorted()
            return candidates[random.nextInt(candidates.size)]
        }
    }
}

/**
 * How fast a round went, as one of the metronome's animals ([TempoBand]) rather than a time:
 * the average time per note, the cheetah for under two seconds down to the snail for over
 * ten. Null until the round is over.
 */
fun NotesRound.speed(): TempoBand? {
    val perNote = (roundMillis ?: return null) / NotesRound.ROUND_LENGTH
    return when {
        perNote <= 2_000 -> TempoBand.CHEETAH
        perNote <= 3_500 -> TempoBand.RABBIT
        perNote <= 6_000 -> TempoBand.DUCK
        perNote <= 10_000 -> TempoBand.TURTLE
        else -> TempoBand.SNAIL
    }
}

/**
 * How well the child aimed, one to three stars from [NotesRound.accuracyPercent]: three
 * from 90 %, two from 70 %, one otherwise (they found every note in the end).
 */
fun NotesRound.aimStars(): Int {
    val percent = accuracyPercent ?: return 1
    return when {
        percent >= 90 -> 3
        percent >= 70 -> 2
        else -> 1
    }
}
