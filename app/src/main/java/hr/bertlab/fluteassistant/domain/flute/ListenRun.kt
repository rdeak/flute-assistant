// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice

/** A wrong note heard in "Your turn": [midi] came out for the step at [index] of the tune. */
data class WrongNote(val index: Int, val midi: Int)

/**
 * What the screen shows of a "Your turn" in progress.
 *
 * @property index the step of the tune waited on; once the last note is counted it stays on that
 * note while its sound ends ([finishing]).
 * @property wrong the wrong note last heard for the step waited on, kept until the next note is
 * judged (no timer: the child reads it at their own pace).
 * @property hearing what the microphone makes of sound that yields no note.
 * @property heardNow the note the phone hears right now; null in silence and while the note just
 * counted goes on (its tail is not what the next step hears), unless that next step is a written
 * repeat of it, which is just when the child must see it.
 * @property repeatHeld the step waited on is a written repeat of the note just counted and that
 * note is still going on, [ListenRun.REPEAT_ADVICE_FRAMES] after it began: the child is holding
 * it instead of starting it again, which the phone can only hear as one note.
 * @property finishing from the last note being counted until its sound ends (or
 * [ListenRun.FINISH_FRAMES] pass): the run goes on so the screen does not snap back while the
 * child is still playing.
 */
data class Listening(
    val index: Int?,
    val wrong: WrongNote? = null,
    val hearing: Hearing = Hearing.CLEAR,
    val heardNow: Int? = null,
    val repeatHeld: Boolean = false,
    val finishing: Boolean = false,
) {
    /**
     * The one line under the staff: stop and play it again when a written repeat is held on
     * instead, else come closer or play one clear note when the sound stays unclear, else nothing.
     */
    val advice: TunerAdvice
        get() = when {
            repeatHeld -> TunerAdvice.PLAY_AGAIN
            hearing == Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
            hearing == Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
            else -> TunerAdvice.NONE
        }
}

/**
 * One "Your turn" over [tune], a microphone frame at a time: [NoteOnsetTracker] says when a new
 * note begins, [TuneCheck] judges it against the note waited on, and [HearingTracker] judges
 * sound that never becomes a note. [listening] is what the screen shows after each frame and
 * [marks] what the steps have earned. The last note judged does not end the run at once: it goes
 * on ([Listening.finishing]) until that note's sound ends or [FINISH_FRAMES] pass, and then
 * [isOver]. Nothing is timed otherwise; the child sets the pace.
 *
 * Pure Kotlin. The flute's view model feeds it the microphone, and the tests and the replay of a
 * recorded session feed it audio, so all three hear a run by the same rules. [rules] and [floor]
 * (the room's level the tracker starts from, the one the run before ended with) are the tracker's.
 */
class ListenRun(
    private val tune: List<TuneItem>,
    rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules(),
    floor: Float? = null,
) {
    /** A note that was judged: the step it was judged for, that step's written note, the note that began and what it earned. */
    data class Heard(val index: Int, val expected: Int, val onset: NoteOnsetTracker.Onset, val verdict: Verdict)

    /**
     * What one frame did.
     *
     * @property row the frame as the session recorder keeps it.
     * @property heard the note judged on this frame, if one was.
     * @property forgiven the written repeats this frame's judgement passed over ([TuneCheck.forgiven]).
     * @property hearingMean the mean level that decided [Listening.hearing], when it changed on this frame.
     * @property advanced a step was earned: the animal hops on.
     * @property done the tune's last note was judged on this frame.
     */
    class Step(
        val row: ListenFrame,
        val heard: Heard?,
        val forgiven: List<Int>,
        val hearingMean: Double?,
        val advanced: Boolean,
        val done: Boolean,
    )

    private val check = TuneCheck(tune)
    private val tracker = NoteOnsetTracker(rules, floor)
    private val hearingTracker = HearingTracker()
    /** Frames since the last note was counted while its sound goes on. */
    private var finishFrames = 0

    var listening = Listening(index = check.index)
        private set

    /** What each judged step earned so far, by its index in the tune. */
    val marks: Map<Int, StepMark> get() = check.marks

    /** True once the last note has been counted and its sound has ended, or gone on for [FINISH_FRAMES]. */
    var isOver = false
        private set

    /** The room's own level as the tracker knows it now; the next run starts from it. */
    val floor: Float? get() = tracker.floor

    fun onFrame(frame: PitchFrame): Step {
        val result = tracker.onFrame(frame)
        val runMean = hearingTracker.levelMean // read before onFrame, which empties the window when the run ends
        val hearing = hearingTracker.onFrame(frame.level, result.decision)
        val hearingMean = if (hearing == listening.hearing) null else if (hearing == Hearing.CLEAR) runMean else hearingTracker.levelMean
        listening = listening.copy(hearing = hearing)
        val wasFinishing = listening.finishing
        val waited = check.index
        val heard = result.onset?.let(::judge)
        if (!wasFinishing && !listening.finishing) followSound()
        val row = ListenFrame(
            frame = frame, decision = result.decision, sounding = tracker.sounding, onset = result.onset, floor = tracker.floor,
            verdict = heard?.verdict, waited = waited, expected = waited?.let(::noteAt),
            heardNow = listening.heardNow, advice = listening.advice, repeatHeld = listening.repeatHeld,
            finishing = listening.finishing, hearing = listening.hearing,
        )
        if (wasFinishing && (tracker.sounding == null || ++finishFrames >= FINISH_FRAMES)) isOver = true
        val advanced = heard?.verdict == Verdict.RIGHT || heard?.verdict == Verdict.RETRY
        return Step(row, heard, check.forgiven.takeIf { heard != null }.orEmpty(), hearingMean, advanced, done = advanced && check.isDone)
    }

    /** A new note began ([onset]): judge it against the note waited on; null when nothing is waited on. */
    private fun judge(onset: NoteOnsetTracker.Onset): Heard? {
        val index = check.index ?: return null
        val expected = noteAt(index)
        val verdict = check.judge(onset.midi, onset.fresh)
        when (verdict) {
            Verdict.RIGHT, Verdict.RETRY -> {
                val done = check.isDone
                // Done: the animal stays over the last note while its sound ends.
                listening = listening.copy(
                    index = if (done) index else check.index, finishing = done, wrong = null, heardNow = null, repeatHeld = false,
                )
                if (done) finishFrames = 0
            }
            // The note heard stays under the step until the next note is judged: no timer, so there
            // is time to read it; an ignored note (the last step's tail or octave pop) leaves it.
            Verdict.WRONG -> listening = listening.copy(wrong = WrongNote(index, onset.midi))
            Verdict.IGNORED -> Unit
        }
        return Heard(index, expected, onset, verdict)
    }

    /**
     * What the step waited on hears right now ([Listening.heardNow]): the sounding note, unless
     * it is the tail of the note just counted and something else is waited on; and whether that
     * tail is a written repeat held on instead of started again ([Listening.repeatHeld]), once it
     * has gone on for [REPEAT_ADVICE_FRAMES].
     */
    private fun followSound() {
        val expected = noteAt(check.index ?: return)
        val sounding = tracker.sounding
        val previous = check.previous
        val ofPrevious = sounding != null && previous != null && TuneCheck.sameLetter(sounding, previous)
        val ofExpected = sounding != null && TuneCheck.sameLetter(sounding, expected)
        listening = listening.copy(
            heardNow = sounding.takeUnless { ofPrevious && !ofExpected },
            repeatHeld = ofPrevious && ofExpected && tracker.framesSinceOnset >= REPEAT_ADVICE_FRAMES,
        )
    }

    private fun noteAt(index: Int): Int = (tune[index] as TuneNote).midi

    companion object {
        /**
         * Counted frames a note must go on after being counted, with its written repeat waited
         * on, before the advice line asks the child to stop and play it again: about a second,
         * longer than any note the child means as one before a repeat at the ring's tempos.
         */
        const val REPEAT_ADVICE_FRAMES = 25

        /**
         * Frames after the last note is counted after which the run ends even though its sound
         * goes on: about 1.5 s, so a child holding the last note long still gets the keys back.
         */
        const val FINISH_FRAMES = 35
    }
}
