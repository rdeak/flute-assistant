// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice

/**
 * What the phone did with one microphone frame of a "Your turn" run: the frame itself, what
 * the tracker made of it ([decision], [sounding], [onset], its room [floor] after the frame),
 * the [verdict] if a note was judged on it, the step waited on before that judgement ([waited],
 * its note [expected]) and what the child saw after it ([heardNow], [advice], [repeatHeld],
 * [finishing], [hearing]).
 */
data class ListenFrame(
    val frame: PitchFrame,
    val decision: FrameDecision,
    val sounding: Int?,
    val onset: NoteOnsetTracker.Onset?,
    val floor: Float?,
    val verdict: Verdict?,
    val waited: Int?,
    val expected: Int?,
    val heardNow: Int?,
    val advice: TunerAdvice,
    val repeatHeld: Boolean,
    val finishing: Boolean,
    val hearing: Hearing,
)

/**
 * Keeps a "Your turn" run for inspection afterwards: [start] when the child presses the button
 * (before the microphone opens, with the tune on the staff, the pulse it was written in and the
 * room's floor the tracker still holds), [frame] for every frame the run processed, in
 * order, [end] with the marks when the run is over. The debug build records every run into a
 * folder on the phone (`ui/flute/CLAUDE.md`, "Sessions"); the release build binds [None] and
 * keeps nothing.
 */
interface ListenRecorder {
    fun start(tune: List<TuneItem>, pulse: Pulse, floor: Float?)
    fun frame(frame: ListenFrame)
    fun end(marks: Map<Int, StepMark>)

    /** Keeps nothing: the release build's recorder. */
    object None : ListenRecorder {
        override fun start(tune: List<TuneItem>, pulse: Pulse, floor: Float?) = Unit
        override fun frame(frame: ListenFrame) = Unit
        override fun end(marks: Map<Int, StepMark>) = Unit
    }
}
