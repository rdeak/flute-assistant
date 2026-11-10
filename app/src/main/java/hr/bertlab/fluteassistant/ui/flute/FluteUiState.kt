// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Held
import hr.bertlab.fluteassistant.domain.flute.Listening
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.WrongNote
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice

/**
 * Play in progress: the step of the tune playing ([index]) and how many half beats into it the
 * metronome's last click fell ([halfBeat], 0 from the step's start until a click inside it), which
 * is the beat the animal stands over.
 */
data class Playing(val index: Int, val halfBeat: Int = 0)

/**
 * Immutable snapshot of everything the flute tab renders. The tab is in one of four ways at a
 * time, and each has its own part of the state: a key under a finger ([held]), the tune playing
 * back ([playing]), "Your turn" listening ([listening]), or none of them, the tune at rest.
 */
data class FluteUiState(
    /** The child's tune, oldest first; saved on every change. */
    val tune: List<TuneItem> = emptyList(),
    /** The key under a finger right now, a note key sounding or the rest key, its step growing; null when none. */
    val held: Held? = null,
    /** The step playing back; null when the tune does not play. */
    val playing: Playing? = null,
    /** What "Your turn" shows while the microphone is open and the child plays the tune; null otherwise. */
    val listening: Listening? = null,
    /**
     * Goes up by one on every click while the tune plays back, and on every step the animal hops
     * to while the flute listens; never reset, only its changes key the animal's hop (like
     * `MetronomeUiState.beatPulse`).
     */
    val beatPulse: Long = 0L,
    /** What each step earned in the last "Your turn", by its index in [tune]; cleared by a new run and by any change to the tune. */
    val marks: Map<Int, StepMark> = emptyMap(),
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

    val isPlaying: Boolean get() = playing != null
    val isListening: Boolean get() = listening != null
    val isEmpty: Boolean get() = tune.isEmpty()

    /** Whether the tune is running, by the phone (Play) or by the child (Your turn): the keys hide and the staff grows. */
    val isRunning: Boolean get() = isPlaying || isListening

    /** Whether there is a note to check: rests and breaths alone give "Your turn" nothing to do. */
    val canListen: Boolean get() = tune.any { it is TuneNote }

    /** The note key drawn pressed: the playing note's, else the held key. */
    val litMidi: Int? get() = playing?.let { (tune.getOrNull(it.index) as? TuneNote)?.midi } ?: held?.midi

    /** Whether the rest key is drawn pressed: held, or its rest playing. */
    val restLit: Boolean get() = held?.isRest == true || playing?.let { tune.getOrNull(it.index) is TuneRest } == true

    /** Where the held key's note or rest sits in [tune]: the step with the yellow band; null at the cap, where a press is not added. */
    val heldIndex: Int? get() = held?.index

    /** The step of the staff ringed in yellow: the playing step, else the one waited for (none once the last is counted), else the held key's. */
    val litIndex: Int? get() = playing?.index ?: listening?.takeUnless { it.finishing }?.index ?: heldIndex

    /** The step the knob's animal stands over while the tune runs: the playing step, else the one waited for, or the last counted while its sound ends; null at rest. */
    val animalIndex: Int? get() = playing?.index ?: listening?.index

    /** Whether the animal waits for the child to play the step it stands over: listening, and not finishing. */
    val waiting: Boolean get() = listening?.let { !it.finishing && it.index != null } == true

    /** How many half beats into [animalIndex]'s step the animal stands: the last click's in Play, the step's start while listening. */
    val animalHalfBeat: Int get() = playing?.halfBeat ?: 0

    /** The wrong note last heard for the step waited on, a bubble under that step ([Listening.wrong]). */
    val wrong: WrongNote? get() = listening?.wrong

    /** The note the phone hears right now, a live bubble under the step waited on ([Listening.heardNow]). */
    val heardNow: Int? get() = listening?.heardNow

    /** The one line under the staff while listening ([Listening.advice]); nothing otherwise. */
    val advice: TunerAdvice get() = listening?.advice ?: TunerAdvice.NONE
}
