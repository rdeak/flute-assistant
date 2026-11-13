// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import kotlin.math.roundToLong

/** A wrong note heard in "Your turn": [midi] came out for the step at [index] of the tune. */
data class WrongNote(val index: Int, val midi: Int)

/**
 * Where the knob's animal is in "Your turn" when it is not simply waiting at the head of the step waited
 * on: on the step at [index], [halfBeat] half beats into it (the place of a click inside it, as in Play),
 * and why ([phase]).
 */
data class Walk(val index: Int, val halfBeat: Int, val phase: Phase) {
    enum class Phase {
        /** The step's right note sounds and is not long enough yet: the animal walks it on the clicks. */
        SOUNDING,

        /** The note stopped before it was long enough: the animal stays where it got to until it is played again. */
        SHORT,

        /** The note was long enough and is earned: the animal finishes its written time, then goes to the next note. */
        DONE,
    }
}

/**
 * What the screen shows of a "Your turn" in progress.
 *
 * @property index the step of the tune waited on, the one a note heard is judged against; once
 * the last note is earned it stays on that note while its sound ends ([finishing]).
 * @property wrong the wrong note last heard for the step waited on, kept until the next note is
 * judged (no timer: the child reads it at their own pace).
 * @property hearing what the microphone makes of sound that yields no note.
 * @property heardNow the note the phone hears right now; null in silence and while the note just
 * counted goes on (its tail is not what the next step hears), unless that next step is a written
 * repeat of it, which is just when the child must see it.
 * @property repeatHeld the step waited on is a written repeat of the note just counted and that
 * note is still going on, [ListenRun.REPEAT_ADVICE_FRAMES] after its written length ended: the
 * child is holding it instead of starting it again, which the phone can only hear as one note.
 * @property finishing from the last note being earned until its sound ends (or
 * [ListenRun.FINISH_FRAMES] pass): the run goes on so the screen does not snap back while the
 * child is still playing.
 * @property walk where the animal is while a note is walked, cut short or finishing its time; null
 * while it waits at the head of [index].
 */
data class Listening(
    val index: Int?,
    val wrong: WrongNote? = null,
    val hearing: Hearing = Hearing.CLEAR,
    val heardNow: Int? = null,
    val repeatHeld: Boolean = false,
    val finishing: Boolean = false,
    val walk: Walk? = null,
) {
    /**
     * The one line under the staff: stop and play it again when a written repeat is held on
     * instead, else come closer or play one clear note when the sound stays unclear, else hold it
     * longer while a note cut short waits to be played again (since 2026-11-11), else check the
     * fingers while a wrong note stands (since 2026-11-10: the heard note said what came out, and
     * nothing said what to do about it), else nothing. A note cut short and a wrong note never
     * stand together: a note heard while one is cut short is not a wrong note.
     */
    val advice: TunerAdvice
        get() = when {
            repeatHeld -> TunerAdvice.PLAY_AGAIN
            hearing == Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
            hearing == Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
            walk?.phase == Walk.Phase.SHORT -> TunerAdvice.HOLD_LONGER
            wrong != null -> TunerAdvice.CHECK_FINGERS
            else -> TunerAdvice.NONE
        }
}

/**
 * One "Your turn" over [tune], a microphone frame at a time: [NoteOnsetTracker] says when a new
 * note begins, [TuneCheck] judges its pitch against the note waited on, and [HearingTracker]
 * judges sound that never becomes a note. [listening] is what the screen shows after each frame
 * and [marks] what the steps have earned.
 *
 * The child decides when each note starts; the metronome's [pulse] decides how long it lasts
 * (since 2026-11-11, `docs/superpowers/specs/2026-11-11-flute-your-turn-note-length-design.md`).
 * A right note is walked ([Walk]): from the moment its sound began (the frames that locked it
 * included) the animal steps to each click inside it, and the note is earned once it has sounded
 * its written length less [Rules.lengthSlack] half beats, and never more than [Rules.maxNeedMs] (a
 * slow tempo's long note is beyond a child's breath). A note that stops first (silence, or another
 * note; an octave pop, a slow sag or its own note started again with no gap is the same note going
 * on, [Rules.restartCuts]) is cut short: the animal stays where it got to, the run waits on it again, and it earns a faint
 * star once played long enough. An earned note's written time is finished on the clicks, sound or
 * not, and then the animal goes to the next note, at once if the child starts it sooner. Time is
 * the sum of the frames' own lengths ([PitchFrame.hopMs]), never a clock, so the app, the
 * synthetic tests and the replay of a recorded session hear a run alike. The time between notes
 * and the rests are not judged.
 *
 * The last note earned does not end the run at once: it goes on ([Listening.finishing]) until
 * that note's sound ends or [Rules.finishFrames] pass, and then [isOver].
 *
 * Pure Kotlin. The flute's view model feeds it the microphone, and the tests and the replay of a
 * recorded session feed it audio, so all three hear a run by the same rules. [rules] and [floor]
 * (the room's level the tracker starts from, the one the run before ended with) are the tracker's;
 * [run] are the run's own ([Rules]), which [Rules.PITCH_ONLY] turns back into the pitch-only rule a
 * session recorded before 2026-11-11 ran under.
 */
class ListenRun(
    private val tune: List<TuneItem>,
    private val pulse: Pulse,
    rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules(),
    floor: Float? = null,
    private val run: Rules = Rules(),
) {
    /**
     * The run's own rules, beside the tracker's [NoteOnsetTracker.Rules] and made like them: a value whose
     * defaults are the constants, which the app runs with, a session writes down ([fields]) and a replay moves
     * one at a time ([with]).
     *
     * @property lengthSlack half beats of its written length a note may stop short of ([LENGTH_SLACK]).
     * @property maxNeedMs the longest any note needs to sound ([MAX_NEED_MS]).
     * @property repeatAdviceFrames [REPEAT_ADVICE_FRAMES].
     * @property finishFrames [FINISH_FRAMES].
     * @property restartCuts whether the note walked, not yet long enough, is cut short when the tracker hears its own
     * letter started again with no gap between (a tongued stop, or a breath dipping): the rule until 2026-11-13 (1 in a
     * session's line). Since then it goes on (0): the sessions of 2026-11-12 showed a child's breath dipping 10 dB
     * inside a whole note, which the tracker hears as a stop, and the note was cut and walked again three times while
     * the child never stopped. Only a gap, sound that ends, cuts a note now.
     */
    data class Rules(
        val lengthSlack: Double = LENGTH_SLACK,
        val maxNeedMs: Double = MAX_NEED_MS,
        val repeatAdviceFrames: Int = REPEAT_ADVICE_FRAMES,
        val finishFrames: Int = FINISH_FRAMES,
        val restartCuts: Boolean = false,
    ) {
        /** Name and value of every rule, in the order a session's `session.rules` line has them after the tracker's. */
        fun fields(): List<Pair<String, String>> = listOf(
            "repeatAdviceFrames" to repeatAdviceFrames.toString(), "finishFrames" to finishFrames.toString(),
            "lengthSlack" to lengthSlack.toString(), "maxNeedMs" to maxNeedMs.toString(),
            "restartCuts" to if (restartCuts) "1" else "0",
        )

        /** These rules with the one named [name] set to [value] (rounded for a frame count, not 0 for a yes); an unknown name is refused. */
        fun with(name: String, value: Double): Rules = when (name) {
            "lengthSlack" -> copy(lengthSlack = value)
            "maxNeedMs" -> copy(maxNeedMs = value)
            "repeatAdviceFrames" -> copy(repeatAdviceFrames = Math.round(value).toInt())
            "finishFrames" -> copy(finishFrames = Math.round(value).toInt())
            "restartCuts" -> copy(restartCuts = value != 0.0)
            else -> throw IllegalArgumentException("no rule named $name")
        }

        companion object {
            /** The names [with] knows. */
            val NAMES: Set<String> = Rules().fields().map { it.first }.toSet()

            /**
             * No length rule: no slack to run out of and no longest hold, so every note is long enough the
             * moment it is heard. The verdicts and marks are those of before 2026-11-11 (the animal still
             * finishes each note's written time, which a replay of such a session does not compare). A restart
             * cuts as it did then, so a session that names the length rule and not [restartCuts] (recorded on
             * 2026-11-11 or 12) replays with the cut it ran under.
             */
            val PITCH_ONLY = Rules(lengthSlack = Double.POSITIVE_INFINITY, maxNeedMs = Double.POSITIVE_INFINITY, restartCuts = true)
        }
    }

    /** A note that was judged: the step it was judged for, that step's written note, the note that began and what it earned. */
    data class Heard(val index: Int, val expected: Int, val onset: NoteOnsetTracker.Onset, val verdict: Verdict)

    /** How long the note of the step at [index] sounded ([heldMs]) against what it needed ([needMs]). */
    data class NoteTime(val index: Int, val heldMs: Long, val needMs: Long)

    /**
     * What one frame did.
     *
     * @property row the frame as the session recorder keeps it.
     * @property heard the note judged on this frame, if one was.
     * @property forgiven the written repeats this frame's judgement passed over ([TuneCheck.forgiven]).
     * @property hearingMean the mean level that decided [Listening.hearing], when it changed on this frame.
     * @property advanced a step was earned on this frame (long enough): its mark shows.
     * @property done the tune's last note was earned on this frame.
     * @property hop the animal hops on this frame, to another place: a click inside the note it walks, the next
     * note's head, or back to the head of a note played again. A note beginning where it stands is no hop.
     * @property short a note was cut short on this frame.
     * @property held a note became long enough on this frame.
     */
    class Step(
        val row: ListenFrame,
        val heard: Heard?,
        val forgiven: List<Int>,
        val hearingMean: Double?,
        val advanced: Boolean,
        val done: Boolean,
        val hop: Boolean = false,
        val short: NoteTime? = null,
        val held: NoteTime? = null,
    )

    /** What the walk did on one frame, gathered for its [Step]. */
    private class Moves {
        var hop = false
        var advanced = false
        var short: NoteTime? = null
        var held: NoteTime? = null
    }

    private val check = TuneCheck(tune)
    private val tracker = NoteOnsetTracker(rules, floor)
    private val hearingTracker = HearingTracker()
    /** Frames since the last note was counted while its sound goes on. */
    private var finishFrames = 0
    /** Each step's clicks after its start, in half beats, where Play puts them. */
    private val clicks: Map<Int, List<Int>> =
        Tune.playSteps(tune, pulse).associate { step -> step.index to step.inside.mapNotNull { it.halfBeat } }
    private val halfBeatMs = pulse.quarterMs / 2
    /** The run's own clock: the frames' lengths added up, in ms. */
    private var nowMs = 0.0
    /** When the note walked began, on [nowMs]'s clock. */
    private var startMs = 0.0
    /**
     * How long the note walked has sounded, as far as the phone is sure of it: to the last frame the tracker showed
     * it, and no more than [graceFrames] past that, the frames a gap takes to be seen. The tracker holds a note on
     * through loud frames with no pitch for most of a second (a pause is not a blink); that is not the note's time.
     */
    private var heldMs = 0.0
    /** [nowMs] at the last frame the tracker showed the note walked. */
    private var sureMs = 0.0
    private val graceFrames = rules.onsetGapFrames
    /** The length of the frame being heard. */
    private var hopMs = 0.0
    /** Frames since the animal finished an earned note's written time: what a written repeat held on is counted from. */
    private var pastLengthFrames = 0

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
        hopMs = frame.hopMs
        nowMs += hopMs
        val result = tracker.onFrame(frame)
        val runMean = hearingTracker.levelMean // read before onFrame, which empties the window when the run ends
        val hearing = hearingTracker.onFrame(frame.level, result.decision)
        val hearingMean = if (hearing == listening.hearing) null else if (hearing == Hearing.CLEAR) runMean else hearingTracker.levelMean
        listening = listening.copy(hearing = hearing)
        val wasFinishing = listening.finishing
        val waited = check.index
        val moves = Moves()
        var onset = result.onset
        when (listening.walk?.phase) {
            Walk.Phase.SOUNDING -> if (goesOn(onset)) {
                onset = null // an octave pop of the note walked, or its own letter again with no gap: the same note going on
                walkOn(shown = result.decision == FrameDecision.SHOWN, moves)
            } else {
                cutShort(moves)
            }
            Walk.Phase.DONE -> finishTime(moves)
            Walk.Phase.SHORT -> Unit
            null -> pastLengthFrames++
        }
        // The note began inside the first of its frames: that frame's hop counts in full, and the ones that locked it.
        val heard = onset?.let { judge(it, (result.leadFrames + 1) * frame.hopMs, moves) }
        if (!wasFinishing && !listening.finishing) followSound()
        val row = ListenFrame(
            frame = frame, decision = result.decision, sounding = tracker.sounding, onset = result.onset, floor = tracker.floor,
            verdict = heard?.verdict, waited = waited, expected = waited?.let(::noteAt),
            heardNow = listening.heardNow, advice = listening.advice, repeatHeld = listening.repeatHeld,
            finishing = listening.finishing, hearing = listening.hearing, walk = listening.walk,
        )
        if (wasFinishing && (tracker.sounding == null || ++finishFrames >= run.finishFrames)) isOver = true
        return Step(
            row, heard, check.forgiven.takeIf { heard != null }.orEmpty(), hearingMean,
            advanced = moves.advanced, done = moves.advanced && check.isDone,
            hop = moves.hop, short = moves.short, held = moves.held,
        )
    }

    /**
     * Whether the note walked goes on through this frame: the tracker still holds a note (its own,
     * or a neighbour it sagged into with no onset) and nothing new began but its own letter, by an
     * octave pop or, unless [Rules.restartCuts], started again with no gap (while the tracker sounds, a
     * fresh onset is that). How much of that is the note's time is [walkOn]'s to say.
     */
    private fun goesOn(onset: NoteOnsetTracker.Onset?): Boolean {
        val walked = noteAt(listening.walk!!.index)
        return tracker.sounding != null &&
            (onset == null || (TuneCheck.sameLetter(onset.midi, walked) && (!onset.fresh || !run.restartCuts)))
    }

    /** A new note began ([onset], [leadMs] before this frame's end, its first frame's hop included): judge it against the note waited on; null when nothing is waited on. */
    private fun judge(onset: NoteOnsetTracker.Onset, leadMs: Double, moves: Moves): Heard? {
        val index = check.index ?: return null
        val expected = noteAt(index)
        // A walk cut short stands on the step waited on: its note was begun, which the check must know.
        val verdict = check.judge(onset.midi, onset.fresh, begun = listening.walk?.phase == Walk.Phase.SHORT)
        when (verdict) {
            Verdict.RIGHT, Verdict.RETRY -> startWalk(leadMs, moves)
            // A note heard while one cut short waits is not wrong: the child went on, the line says hold it longer.
            Verdict.WRONG -> if (listening.walk?.phase != Walk.Phase.SHORT) {
                // The note heard stays under the step until the next note is judged: no timer, so there
                // is time to read it. The animal leaves an earned note's last half beat for it at once.
                if (listening.walk != null) {
                    moves.hop = true
                    pastLengthFrames = 0
                }
                listening = listening.copy(walk = null, wrong = WrongNote(index, onset.midi))
            }
            // An ignored note (the last step's tail or octave pop) leaves everything as it was.
            Verdict.IGNORED -> Unit
        }
        return Heard(index, expected, onset, verdict)
    }

    /**
     * The right note for the step waited on began [leadMs] ago: the animal walks it from its head,
     * which it reaches with a hop unless it stands there already (it never hops twice in one place:
     * the user's rule of 2026-11-04).
     */
    private fun startWalk(leadMs: Double, moves: Moves) {
        val index = check.index!! // the heard note's step, after any repeats forgiven
        val from = place()
        startMs = nowMs - leadMs
        listening = listening.copy(index = index, walk = Walk(index, 0, Walk.Phase.SOUNDING), wrong = null, heardNow = null, repeatHeld = false)
        if (place() != from) moves.hop = true
        walkOn(shown = true, moves)
    }

    /** Where the animal stands: the step and the half beat into it; the head of the step waited on when it walks nothing. */
    private fun place(): Pair<Int?, Int> = listening.walk?.let { it.index to it.halfBeat } ?: (listening.index to 0)

    /**
     * The note walked goes on through this frame, [shown] by the tracker or only held by it: to the click it
     * reached, and earned once it is long enough. A stretch the tracker only held (the gate blinking on a breathy
     * tone) counts in full once the note is shown again.
     */
    private fun walkOn(shown: Boolean, moves: Moves) {
        if (shown) sureMs = nowMs
        heldMs = minOf(nowMs, sureMs + graceFrames * hopMs) - startMs
        stepTo(heldMs, moves)
        val walk = listening.walk!!
        if (heldMs + EPSILON_MS >= needMs(walk.index)) earn(walk.index, moves)
    }

    private fun earn(index: Int, moves: Moves) {
        check.earn()
        moves.advanced = true
        moves.held = NoteTime(index, heldMs.roundToLong(), needMs(index).roundToLong())
        val done = check.isDone
        // Done: the animal stays over the last note while its sound ends.
        listening = listening.copy(
            index = if (done) index else check.index,
            walk = listening.walk!!.copy(phase = Walk.Phase.DONE),
            finishing = done, wrong = null, heardNow = null, repeatHeld = false,
        )
        if (done) finishFrames = 0
    }

    /** The note walked stopped before it was long enough: the animal stays where it got to, and the step will earn a faint star. */
    private fun cutShort(moves: Moves) {
        val walk = listening.walk!!
        check.stumble()
        moves.short = NoteTime(walk.index, heldMs.roundToLong(), needMs(walk.index).roundToLong())
        listening = listening.copy(walk = walk.copy(phase = Walk.Phase.SHORT))
    }

    /** An earned note's written time goes on on the clicks, sound or not; at its end the animal goes to the next note. */
    private fun finishTime(moves: Moves) {
        val since = nowMs - startMs
        stepTo(since, moves)
        val walk = listening.walk!!
        if (!check.isDone && since + EPSILON_MS >= fullMs(walk.index)) {
            listening = listening.copy(walk = null)
            pastLengthFrames = 0
            moves.hop = true
        }
    }

    /** The animal steps to the last click inside the note walked that [since] has reached, with a hop. */
    private fun stepTo(since: Double, moves: Moves) {
        val walk = listening.walk!!
        val place = clicks[walk.index].orEmpty().lastOrNull { it * halfBeatMs <= since + EPSILON_MS } ?: 0
        if (place > walk.halfBeat) {
            listening = listening.copy(walk = walk.copy(halfBeat = place))
            moves.hop = true
        }
    }

    /**
     * What the step waited on hears right now ([Listening.heardNow]): the sounding note, unless
     * it is the tail of the note just counted and something else is waited on; and whether that
     * tail is a written repeat held on instead of started again ([Listening.repeatHeld]), once it
     * has gone on for [REPEAT_ADVICE_FRAMES] frames past its written length, and the sound for as
     * many counted frames since it began (a slur back to it starts that again).
     */
    private fun followSound() {
        val expected = noteAt(check.index ?: return)
        val sounding = tracker.sounding
        val previous = check.previous
        val ofPrevious = sounding != null && previous != null && TuneCheck.sameLetter(sounding, previous)
        val ofExpected = sounding != null && TuneCheck.sameLetter(sounding, expected)
        listening = listening.copy(
            heardNow = sounding.takeUnless { ofPrevious && !ofExpected },
            repeatHeld = ofPrevious && ofExpected && listening.walk == null &&
                pastLengthFrames >= run.repeatAdviceFrames && tracker.framesSinceOnset >= run.repeatAdviceFrames,
        )
    }

    private fun noteAt(index: Int): Int = (tune[index] as TuneNote).midi

    private fun halfBeatsAt(index: Int): Int = (tune[index] as TuneNote).length.halfBeats

    /** How long the note at [index] must sound to be earned: its length less the slack, never under nothing nor over the longest hold. */
    private fun needMs(index: Int): Double = minOf(maxOf(0.0, halfBeatsAt(index) - run.lengthSlack) * halfBeatMs, run.maxNeedMs)

    /** The note's written length at [pulse]. */
    private fun fullMs(index: Int): Double = halfBeatsAt(index) * halfBeatMs

    companion object {
        /**
         * Frames a note must go on after its written length ended (and counted frames since its
         * sound began), with its written repeat waited on, before the advice line asks the child to
         * stop and play it again: about a second, longer than any note the child means as one
         * before a repeat at the ring's tempos.
         */
        const val REPEAT_ADVICE_FRAMES = 25

        /**
         * Frames after the last note is counted after which the run ends even though its sound
         * goes on: about 1.5 s, so a child holding the last note long still gets the keys back.
         */
        const val FINISH_FRAMES = 35

        /**
         * Half beats of a note's written length it may stop short of and still be long enough: a
         * child lets go a little early to breathe or to start the next note. Three quarters of a beat
         * since 2026-11-13 (the user's pick, "count in that the child is learning"): in the first runs
         * recorded with the walk (2026-11-12, 60 a minute) the child stopped half notes at 1.2 to 1.7 s,
         * just after the animal's second hop, and half a beat (1.5 s) cut 5 of 12 of them by 50 to
         * 300 ms; at 1.25 s one is cut, and a half note played as a tap still is. Half a beat until
         * then, from the runs of 2026-11-05. Changed only from recorded sessions: a sweep of `lengthSlack`.
         */
        const val LENGTH_SLACK = 1.5

        /**
         * The longest any note needs to sound, however long it is written at however slow a tempo: about a
         * beginner's breath on a long note. A whole note at 40 a minute would need 5.25 s in 4/4 and 10.5 s in 6/8,
         * and the run would wait on it until Stop. The user's "ok" of 2026-11-11 to a recommended 3 s, to be swept
         * (`maxNeedMs`) on the first sessions recorded with the walk.
         */
        const val MAX_NEED_MS = 3000.0

        /** Room for the sum of frame lengths to miss an exact beat by a rounding error. */
        private const val EPSILON_MS = 1e-6
    }
}
