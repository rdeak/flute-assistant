// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice

/** The step last shown: a note, a rest or a breath (held, last pressed, or playing). */
sealed interface Shown {
    data class Note(val midi: Int) : Shown
    data object Rest : Shown
    data object Breath : Shown
}

/** A wrong note heard in "Your turn": [midi] came out for the step at [index] of the tune. */
data class WrongNote(val index: Int, val midi: Int)

/** Immutable snapshot of everything the flute tab renders. */
data class FluteUiState(
    /** The child's tune, oldest first; saved on every change. */
    val tune: List<TuneItem> = emptyList(),
    /** The note key under a finger right now, sounding; null when none. */
    val heldMidi: Int? = null,
    /** Whether the rest key is under a finger right now, its rest growing. */
    val restHeld: Boolean = false,
    /** Where the held key's note or rest sits in [tune]; null at the cap, where a press is not added. */
    val heldIndex: Int? = null,
    /** The held key, the last one pressed, or the step playing now; null before the first tap. No view shows it since the staff (2026-11-02). */
    val shown: Shown? = null,
    /** The step of [tune] playing; null when not playing. */
    val playingIndex: Int? = null,
    /**
     * How many half beats into the playing step the metronome's last click fell, 0 from the step's
     * start until a click inside it: the animal stands over that beat ([animalHalfBeat]). Read only
     * while [playingIndex] is set.
     */
    val playingHalfBeat: Int = 0,
    /** How long the held key's note or rest has grown so far ([NoteLength.ofBeats]); null when no key is held. */
    val heldLength: NoteLength? = null,
    /** The metronome tab's time signature, which the staff is written in (bar lines, ties). */
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    /**
     * Goes up by one on every click while the tune plays back, and on every step the animal hops
     * to while the flute listens; never reset, only its changes key the animal's hop (like
     * `MetronomeUiState.beatPulse`).
     */
    val beatPulse: Long = 0L,
    /** Whether "Your turn" is on: the microphone is open and the child plays the tune. */
    val isListening: Boolean = false,
    /** The step of [tune] the flute waits for the child to play; null when not listening. */
    val listenIndex: Int? = null,
    /** What each step earned in the last "Your turn", by its index in [tune]; cleared by a new run and by any change to the tune. */
    val marks: Map<Int, StepMark> = emptyMap(),
    /**
     * The wrong note last heard for the step waited on, shown as a bubble in its colour under that
     * step until the next note is judged (no timer: the child reads it at their own pace); null otherwise.
     */
    val wrong: WrongNote? = null,
    /** What the microphone makes of sound that yields no note while listening; [Hearing.CLEAR] otherwise. */
    val hearing: Hearing = Hearing.CLEAR,
    /**
     * The note the phone hears right now, shown live as a bubble under the step waited on so the
     * child sees it listening; null in silence, when not listening, and while the note just counted
     * goes on (its tail is not what the next step hears), unless that next step is a written
     * repeat of it, which is just when the child must see it.
     */
    val heardNow: Int? = null,
    /**
     * True while the step waited on is a written repeat of the note just counted and that note is
     * still going on, a second or more after it began: the child is holding it instead of starting
     * it again, which the phone can only hear as one note; the advice line says what to do.
     */
    val repeatHeld: Boolean = false,
    /**
     * True from the last note being counted until its sound ends (or a moment passes): the run
     * goes on, the keys stay away and the animal stays over the last note with its star, so the
     * screen does not snap back while the child is still playing.
     */
    val finishing: Boolean = false,
    /** True after the phone said no to the microphone on this tab, while the microphone card is shown. */
    val micDenied: Boolean = false,
    /** Which of the [Songbook.SIZE] books of the shelf is open: the one [tune] belongs to and every change is saved to. */
    val openBook: Int = 0,
    /** The shelf as last stored; the open entry may lag behind [tune], so the shelf draws [shelf]. */
    val books: List<Book> = List(Songbook.SIZE) { Book() },
) {
    /** [books] with the open entry's tune replaced by [tune], the tune as the child sees it now. */
    val shelf: List<Book>
        get() = books.mapIndexed { index, book -> if (index == openBook) book.copy(tune = tune) else book }

    val isPlaying: Boolean get() = playingIndex != null
    val isEmpty: Boolean get() = tune.isEmpty()
    val atCap: Boolean get() = tune.size >= Tune.MAX_NOTES

    /** Whether the tune is running, by the phone (Play) or by the child (Your turn): the keys hide and the staff grows. */
    val isRunning: Boolean get() = isPlaying || isListening

    /** Whether there is a note to check: rests and breaths alone give "Your turn" nothing to do. */
    val canListen: Boolean get() = tune.any { it is TuneNote }

    /** Whether a note key or the rest key is under a finger. */
    val isHolding: Boolean get() = heldMidi != null || restHeld

    /** [shown] when it is a note; null for a rest, a breath or nothing yet. */
    val shownMidi: Int? get() = (shown as? Shown.Note)?.midi

    /** The note key drawn pressed: the playing note's, else the held key. */
    val litMidi: Int? get() = playingIndex?.let { (tune.getOrNull(it) as? TuneNote)?.midi } ?: heldMidi

    /** Whether the rest key is drawn pressed: held, or its rest playing. */
    val restLit: Boolean get() = restHeld || playingIndex?.let { tune.getOrNull(it) is TuneRest } == true

    /** The step of the staff ringed in yellow: the playing step, else the one waited for (none once the last is counted), else the held key's. */
    val litIndex: Int? get() = playingIndex ?: listenIndex.takeUnless { finishing } ?: heldIndex

    /** The step the knob's animal stands over while the tune runs: the playing step, else the one waited for, or the last counted while its sound ends; null at rest. */
    val animalIndex: Int? get() = playingIndex ?: listenIndex

    /** Whether the animal waits for the child to play [listenIndex]: listening, and not finishing. */
    val waiting: Boolean get() = isListening && !finishing && listenIndex != null

    /** How many half beats into [animalIndex]'s step the animal stands: the last click's in Play, the step's start while listening. */
    val animalHalfBeat: Int get() = if (playingIndex != null) playingHalfBeat else 0

    /**
     * The one line under the staff while listening: stop and play it again when a written repeat
     * is held on instead, else come closer or play one clear note when the sound stays unclear,
     * else nothing.
     */
    val advice: TunerAdvice
        get() = when {
            repeatHeld -> TunerAdvice.PLAY_AGAIN
            hearing == Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
            hearing == Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
            else -> TunerAdvice.NONE
        }
}
