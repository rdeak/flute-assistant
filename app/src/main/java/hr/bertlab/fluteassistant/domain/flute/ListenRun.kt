// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.roundToLong

/**
 * A wrong note heard in "Your turn": [midi] came out for the step at [index] of the tune. [tooHigh]: it was one
 * semitone above the written note and within [ListenRules.NEAR_CENTS] of it, so the right fingers blown high may have
 * made it, and the line says so as a guess.
 */
data class WrongNote(val index: Int, val midi: Int, val tooHigh: Boolean = false)

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

        /**
         * A rest after an earned note's written time, or opening or ending the tune: the animal walks its clicks in
         * silence, as in Play, then goes to the next note ([ListenRun.Rules.walkRests]).
         */
        REST,
    }
}

/**
 * What the screen shows of a "Your turn" in progress.
 *
 * @property index the step of the tune waited on, the one a note heard is judged against (a rest is
 * never waited on); once the last note is earned it stays on that note, while its sound ends
 * ([finishing]) or a rest after it is walked.
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
 * child is still playing. Not with a rest after the last note: that rest is walked, and its end
 * ends the run.
 * @property walk where the animal is while a note is walked, cut short or finishing its time, or a
 * rest is walked; null while it waits at the head of [index].
 * @property faded the note cut short faded away before it stopped ([ListenRun.FADE_RATIO]): the line
 * guesses the air ran out; false again once a note is walked.
 */
data class Listening(
    val index: Int?,
    val wrong: WrongNote? = null,
    val hearing: Hearing = Hearing.CLEAR,
    val heardNow: Int? = null,
    val repeatHeld: Boolean = false,
    val finishing: Boolean = false,
    val walk: Walk? = null,
    val faded: Boolean = false,
) {
    /** The step the animal stands on: the one it walks, else the one waited on. */
    val animalIndex: Int? get() = walk?.index ?: index

    /** Half beats into [animalIndex] the animal stands: where its walk got to, else the step's head. */
    val animalHalfBeat: Int get() = walk?.halfBeat ?: 0

    /**
     * The one line under the staff: stop and play it again when a written repeat is held on
     * instead, else come closer or play one clear note when the sound stays unclear, else hold it
     * longer while a note cut short waits to be played again (since 2026-11-11), else check the
     * fingers while a wrong note stands (since 2026-11-10: the heard note said what came out, and
     * nothing said what to do about it), else nothing. Two lines are guesses and say so ("maybe"):
     * out of air for a note cut short that had [faded], too high for a wrong note [WrongNote.tooHigh].
     * A note cut short and a wrong note never
     * stand together: a note heard while one is cut short is not a wrong note.
     */
    val advice: TunerAdvice
        get() = when {
            repeatHeld -> TunerAdvice.PLAY_AGAIN
            hearing == Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
            hearing == Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
            walk?.phase == Walk.Phase.SHORT -> if (faded) TunerAdvice.OUT_OF_AIR else TunerAdvice.HOLD_LONGER
            wrong != null -> if (wrong.tooHigh) TunerAdvice.TOO_HIGH else TunerAdvice.CHECK_FINGERS
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
 * not, then the rests after it are walked on theirs as in Play ([Walk.Phase.REST], [Rules.walkRests]),
 * and then the animal goes to the next note, at once if the child starts it sooner. Time is
 * the sum of the frames' own lengths ([PitchFrame.hopMs]), never a clock, so the app, the
 * synthetic tests and the replay of a recorded session hear a run alike. The time between notes
 * and the rests are not judged.
 *
 * The last note earned does not end the run at once: it goes on ([Listening.finishing]) until
 * that note's sound ends or [Rules.finishFrames] pass, and then [isOver]; with a rest after it, the
 * run is over once that rest is walked.
 *
 * Pure Kotlin. The flute's view model feeds it the microphone, and the tests and the replay of a
 * recorded session feed it audio, so all three hear a run by the same rules. [rules] and [floor]
 * (the room's level the tracker starts from, the one the run before ended with) are the tracker's;
 * [run] are the run's own ([Rules]; [Rules.PITCH_ONLY] is the pitch-only rule of before 2026-11-11).
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
     * letter started again with no gap (1 in a session's line; the rule until 2026-11-13). Off, only a gap cuts a
     * note: a child's breath dips like a tongued stop (`DESIGN.md`, "The first runs with the walk").
     * @property graceFrames [GRACE_FRAMES].
     * @property walkRests whether the animal walks the rests (1 in a session's line, since 2026-11-13), as in Play;
     * before, the run passed them over and the animal jumped to the note after a rest.
     */
    data class Rules(
        val lengthSlack: Double = LENGTH_SLACK,
        val maxNeedMs: Double = MAX_NEED_MS,
        val repeatAdviceFrames: Int = REPEAT_ADVICE_FRAMES,
        val finishFrames: Int = FINISH_FRAMES,
        val restartCuts: Boolean = false,
        val graceFrames: Int = GRACE_FRAMES,
        val walkRests: Boolean = true,
    ) {
        /** Name and value of every rule, in the order a session's `session.rules` line has them after the tracker's. */
        fun fields(): List<Pair<String, String>> = listOf(
            "repeatAdviceFrames" to repeatAdviceFrames.toString(), "finishFrames" to finishFrames.toString(),
            "lengthSlack" to lengthSlack.toString(), "maxNeedMs" to maxNeedMs.toString(),
            "restartCuts" to if (restartCuts) "1" else "0", "graceFrames" to graceFrames.toString(),
            "walkRests" to if (walkRests) "1" else "0",
        )

        /** These rules with the one named [name] set to [value] (rounded for a frame count, not 0 for a yes); an unknown name is refused. */
        fun with(name: String, value: Double): Rules = when (name) {
            "lengthSlack" -> copy(lengthSlack = value)
            "maxNeedMs" -> copy(maxNeedMs = value)
            "repeatAdviceFrames" -> copy(repeatAdviceFrames = Math.round(value).toInt())
            "finishFrames" -> copy(finishFrames = Math.round(value).toInt())
            "restartCuts" -> copy(restartCuts = value != 0.0)
            "graceFrames" -> copy(graceFrames = Math.round(value).toInt())
            "walkRests" -> copy(walkRests = value != 0.0)
            else -> throw IllegalArgumentException("no rule named $name")
        }

        companion object {
            /** The names [with] knows. */
            val NAMES: Set<String> = Rules().fields().map { it.first }.toSet()

            /**
             * No length rule: no slack to run out of and no longest hold, so every note is long enough the
             * moment it is heard. The verdicts and marks are those of before 2026-11-11 (the animal still
             * finishes each note's written time, which a replay of such a session does not compare).
             */
            val PITCH_ONLY = Rules(lengthSlack = Double.POSITIVE_INFINITY, maxNeedMs = Double.POSITIVE_INFINITY)
        }
    }

    /** A note that was judged: the step it was judged for, that step's written note, the note that began and what it earned. */
    data class Heard(val index: Int, val expected: Int, val onset: NoteOnsetTracker.Onset, val verdict: Verdict)

    /** How long the note of the step at [index] sounded ([heldMs]) against what it needed ([needMs]); [faded]: cut short, it had faded away. */
    data class NoteTime(val index: Int, val heldMs: Long, val needMs: Long, val faded: Boolean = false)

    /**
     * What one frame did.
     *
     * @property row the frame as the session recorder keeps it.
     * @property heard the note judged on this frame, if one was.
     * @property forgiven the written repeats this frame's judgement passed over ([TuneCheck.forgiven]).
     * @property hearingMean the mean level that decided [Listening.hearing], when it changed on this frame.
     * @property done the tune's last note was earned on this frame.
     * @property hop the animal hops on this frame, to another place: a click inside the note or rest it walks, a
     * rest's head, the next note's head, or back to the head of a note played again. A note beginning where it stands
     * is no hop, nor is a note played again whose first frames already reach the click the animal stopped on: the
     * hop is the frame's whole move, from where the animal stood before it to where it stands after.
     * @property short a note was cut short on this frame.
     * @property held a note became long enough on this frame: it is earned and its mark shows.
     * @property cents the pitch the judged note locked at, in cents from [Heard.expected] (the step waited on when it
     * began); null when none was judged.
     */
    class Step(
        val row: ListenFrame,
        val heard: Heard?,
        val forgiven: List<Int>,
        val hearingMean: Double?,
        val done: Boolean,
        val hop: Boolean = false,
        val short: NoteTime? = null,
        val held: NoteTime? = null,
        val cents: Double? = null,
    ) {
        /** A step was earned on this frame. */
        val advanced: Boolean get() = held != null
    }

    /** What the walk did on one frame, gathered for its [Step]. */
    private class Moves {
        var short: NoteTime? = null
        var held: NoteTime? = null
    }

    /**
     * The time of the note walked on the run's clock ([nowMs]): when it began ([startMs]), the last frame the tracker
     * showed it ([sureMs]), and how long it has sounded as far as the phone is sure of it ([heldMs]: to the last frame
     * shown and no more than [Rules.graceFrames] past it; the tracker holds a note on through loud frames with no
     * pitch for most of a second, a pause and not a blink, and that is not the note's time). A walk's own.
     */
    private class WalkTime(val startMs: Double) {
        var sureMs = startMs
        var heldMs = 0.0
        /** The level of every frame walked, in order: whether the note faded before it stopped. */
        val levels = mutableListOf<Float>()
    }

    private val check = TuneCheck(tune)
    private val tracker = NoteOnsetTracker(rules, floor)
    private val hearingTracker = HearingTracker()
    /** Frames since the last note was counted while its sound goes on. */
    private var framesFinishing = 0
    /** Each step's clicks after its start, in half beats, where Play puts them. */
    private val clicks: Map<Int, List<Int>> =
        Tune.playSteps(tune, pulse).associate { step -> step.index to step.inside.mapNotNull { it.halfBeat } }
    /** The run's own clock: the frames' lengths added up, in ms. */
    private var nowMs = 0.0
    /** The note walked's time; replaced when a walk starts. */
    private var time = WalkTime(0.0)
    /** Frames since the animal finished an earned note's written time: what a written repeat held on is counted from. */
    private var pastLengthFrames = 0

    // A rest that opens the tune is walked from the start, as Play plays it.
    var listening = Listening(index = check.index, walk = restFrom(0)?.let { Walk(it, 0, Walk.Phase.REST) })
        private set

    /** Where the animal stands: its step and the half beat into it. */
    private val place get() = listening.animalIndex to listening.animalHalfBeat

    /** What each judged step earned so far, by its index in the tune. */
    val marks: Map<Int, StepMark> get() = check.marks

    /**
     * True once the last note has been counted and its sound has ended, or gone on for [FINISH_FRAMES]; or, with
     * rests after the last note, once they are walked.
     */
    var isOver = false
        private set

    /** The room's own level as the tracker knows it now; the next run starts from it. */
    val floor: Float? get() = tracker.floor

    fun onFrame(frame: PitchFrame): Step {
        nowMs += frame.hopMs
        val result = tracker.onFrame(frame)
        val runMean = hearingTracker.levelMean // read before onFrame, which empties the window when the run ends
        val hearing = hearingTracker.onFrame(frame.level, result.decision)
        val hearingMean = if (hearing == listening.hearing) null else if (hearing == Hearing.CLEAR) runMean else hearingTracker.levelMean
        listening = listening.copy(hearing = hearing)
        val wasFinishing = listening.finishing
        val waited = check.index
        val stoodOn = place
        val moves = Moves()
        var onset = result.onset
        when (listening.walk?.phase) {
            Walk.Phase.SOUNDING -> if (goesOn(onset)) {
                onset = null // an octave pop of the note walked, or its own letter again with no gap: the same note going on
                walkOn(frame.level, shown = result.decision == FrameDecision.SHOWN, graceMs = run.graceFrames * frame.hopMs, moves)
            } else {
                cutShort(moves)
            }
            Walk.Phase.DONE, Walk.Phase.REST -> writtenTime()
            Walk.Phase.SHORT -> Unit
            null -> pastLengthFrames++
        }
        // The note began inside the first of its frames: that frame's hop counts in full, and the ones that locked it.
        val heard = onset?.let { judge(it, result.lockHz, frame.level, leadMs = (result.leadFrames + 1) * frame.hopMs, moves) }
        if (!wasFinishing && !listening.finishing) followSound()
        val row = ListenFrame(
            frame = frame, decision = result.decision, sounding = tracker.sounding, onset = result.onset, floor = tracker.floor,
            verdict = heard?.verdict, waited = waited, expected = waited?.let(::noteAt),
            heardNow = listening.heardNow, advice = listening.advice, repeatHeld = listening.repeatHeld,
            finishing = listening.finishing, hearing = listening.hearing, walk = listening.walk,
        )
        if (wasFinishing && (tracker.sounding == null || ++framesFinishing >= run.finishFrames)) isOver = true
        return Step(
            row, heard, check.forgiven.takeIf { heard != null }.orEmpty(), hearingMean,
            done = moves.held != null && check.isDone,
            // One hop a frame at most, and only to another place, whatever the walk did on the way there.
            hop = place != stoodOn,
            short = moves.short, held = moves.held,
            cents = heard?.let { h -> result.lockHz?.let { NoteMath.centsBetween(it, h.expected) } },
        )
    }

    /**
     * Whether the note walked goes on through this frame: the tracker still holds a note (its own,
     * or a neighbour it sagged into with no onset) and nothing new began but its own letter, by an
     * octave pop or, unless [Rules.restartCuts], started again with no gap (while the tracker sounds, a
     * fresh onset is that). How much of that is the note's time is [walkOn]'s to say.
     */
    private fun goesOn(onset: NoteOnsetTracker.Onset?): Boolean {
        if (tracker.sounding == null) return false
        if (onset == null) return true
        if (!TuneCheck.sameLetter(onset.midi, noteAt(listening.walk!!.index))) return false
        return !(onset.fresh && run.restartCuts)
    }

    /**
     * A new note began ([onset], [leadMs] before this frame's end, its first frame's hop included, locked at [lockHz],
     * its frame at [level]): judge it against the note waited on; null when nothing is waited on.
     */
    private fun judge(onset: NoteOnsetTracker.Onset, lockHz: Double?, level: Float, leadMs: Double, moves: Moves): Heard? {
        val index = check.index ?: return null
        val expected = noteAt(index)
        // A walk cut short stands on the step waited on: its note was begun, which the check must know.
        val verdict = check.judge(onset.midi, onset.fresh, begun = listening.walk?.phase == Walk.Phase.SHORT)
        when (verdict) {
            Verdict.RIGHT, Verdict.RETRY -> startWalk(leadMs, level, moves)
            Verdict.WRONG -> {
                // The note heard stays under the step until the next note is judged: no timer, so there
                // is time to read it. The animal leaves an earned note's last half beat for it at once.
                if (listening.walk != null) leaveWalk()
                listening = listening.copy(wrong = WrongNote(index, onset.midi, tooHigh = nearAbove(onset.midi, lockHz, expected)))
            }
            // An ignored note (the last step's tail, an octave pop, or the child going on while a note cut short
            // waits) leaves everything as it was.
            Verdict.IGNORED -> Unit
        }
        return Heard(index, expected, onset, verdict)
    }

    /**
     * The right note for the step waited on began [leadMs] ago: the animal walks it from its head, as far as
     * the note has sounded already (it hops there unless it stands there: never twice in one place).
     */
    private fun startWalk(leadMs: Double, level: Float, moves: Moves) {
        val index = check.index!! // the heard note's step, after any repeats forgiven
        time = WalkTime(startMs = nowMs - leadMs)
        listening = listening.copy(
            index = index, walk = Walk(index, 0, Walk.Phase.SOUNDING), wrong = null, heardNow = null, repeatHeld = false, faded = false,
        )
        walkOn(level, shown = true, graceMs = 0.0, moves) // shown on this frame: no grace to add
    }

    /**
     * The note walked goes on through this frame, at [level], [shown] by the tracker or only held by it, [graceMs]
     * past the last frame shown at most: to the click it reached, and earned once it is long enough. A stretch the
     * tracker only held (the gate blinking on a breathy tone) counts in full once the note is shown again.
     */
    private fun walkOn(level: Float, shown: Boolean, graceMs: Double, moves: Moves) {
        time.levels += level
        if (shown) time.sureMs = nowMs
        time.heldMs = minOf(nowMs, time.sureMs + graceMs) - time.startMs
        stepTo(time.heldMs)
        val walk = listening.walk!!
        if (time.heldMs + EPSILON_MS >= needMs(walk.index)) earn(walk.index, moves)
    }

    private fun earn(index: Int, moves: Moves) {
        check.earn()
        moves.held = NoteTime(index, time.heldMs.roundToLong(), needMs(index).roundToLong())
        val done = check.isDone
        // Done: the animal stays over the last note while its sound ends.
        listening = listening.copy(
            index = if (done) index else check.index,
            walk = listening.walk!!.copy(phase = Walk.Phase.DONE),
            // A rest after the last note is walked before the run ends: it is not finishing while its sound ends.
            finishing = done && restFrom(index + 1) == null, wrong = null, heardNow = null, repeatHeld = false,
        )
    }

    /** The note walked stopped before it was long enough: the animal stays where it got to, and the step will earn a faint star. */
    private fun cutShort(moves: Moves) {
        val walk = listening.walk!!
        // Only a note whose sound ended can have run out of air: one that went on into another note was still blown.
        val faded = tracker.sounding == null && faded(time.levels)
        check.stumble()
        moves.short = NoteTime(walk.index, time.heldMs.roundToLong(), needMs(walk.index).roundToLong(), faded)
        listening = listening.copy(walk = walk.copy(phase = Walk.Phase.SHORT), faded = faded)
    }

    /**
     * Whether a note walked through [levels] faded away before it stopped: its last [FADE_FRAMES] frames before the
     * gap took over (the tracker's gap frames, walked, and the frame the sound stopped in) had fallen under
     * [FADE_RATIO] of its own level, the median of the frames before them. Fewer than [FADE_FRAMES] of those is too
     * few to tell: no guess.
     */
    private fun faded(levels: List<Float>): Boolean {
        val gap = tracker.rules.onsetGapFrames
        val own = levels.dropLast(gap + FADE_FRAMES)
        if (own.size < FADE_FRAMES) return false
        val end = levels.dropLast(gap).takeLast(FADE_FRAMES).average()
        return end < FADE_RATIO * own.sorted()[own.size / 2]
    }

    /** Whether [heard], locked at [lockHz], is one semitone above [written] and within [ListenRules.NEAR_CENTS] of it. */
    private fun nearAbove(heard: Int, lockHz: Double?, written: Int): Boolean =
        heard == written + 1 && lockHz != null && NoteMath.centsBetween(lockHz, written) in 0.0..ListenRules.NEAR_CENTS

    /**
     * An earned note's written time, or a rest's, goes on on its clicks, sound or not; at its end the animal walks the
     * rest after it, or goes to the next note's head.
     */
    private fun writtenTime() {
        val walk = listening.walk!!
        val since = nowMs - time.startMs
        stepTo(since)
        if (since + EPSILON_MS < fullMs(walk.index)) return
        val rest = restFrom(walk.index + 1)
        when {
            rest != null -> walkRest(rest, startMs = time.startMs + fullMs(walk.index))
            !check.isDone -> leaveWalk()
            // The rest that ends the tune is walked: the run is over. A last note with no rest after it ends by its
            // sound instead ([Listening.finishing]).
            walk.phase == Walk.Phase.REST -> isOver = true
        }
    }

    /** The animal walks the rest at [index], which began at [startMs] on the run's clock: the beat the step before it ended on. */
    private fun walkRest(index: Int, startMs: Double) {
        time = WalkTime(startMs)
        listening = listening.copy(walk = Walk(index, 0, Walk.Phase.REST))
        stepTo(nowMs - startMs)
    }

    /** The rest the animal walks from step [index] on, breaths passed over; null when a note (or nothing) comes first, or rests are not walked. */
    private fun restFrom(index: Int): Int? {
        if (!run.walkRests) return null
        val next = (index until tune.size).firstOrNull { tune[it] !is TuneBreath } ?: return null
        return next.takeIf { tune[it] is TuneRest }
    }

    /** The animal leaves the note or rest it walks for the head of the step waited on. */
    private fun leaveWalk() {
        listening = listening.copy(walk = null)
        pastLengthFrames = 0
    }

    /** The animal steps to the last click inside the note or rest walked that [since] has reached. */
    private fun stepTo(since: Double) {
        val walk = listening.walk!!
        val place = clicks[walk.index].orEmpty().lastOrNull { it * pulse.halfBeatMs <= since + EPSILON_MS } ?: 0
        if (place > walk.halfBeat) listening = listening.copy(walk = walk.copy(halfBeat = place))
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

    private fun halfBeatsAt(index: Int): Int = tune[index].length?.halfBeats ?: 0

    /** How long the note at [index] must sound to be earned: its length less the slack, never under nothing nor over the longest hold. */
    private fun needMs(index: Int): Double = minOf(maxOf(0.0, halfBeatsAt(index) - run.lengthSlack) * pulse.halfBeatMs, run.maxNeedMs)

    /** The note's written length at [pulse]. */
    private fun fullMs(index: Int): Double = halfBeatsAt(index) * pulse.halfBeatMs

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
         * Half beats of a note's written length it may stop short of and still be long enough, three quarters of a
         * beat: a child lets go a little early, to breathe or to start the next note. Changed only from recorded
         * sessions, a sweep of `lengthSlack` (`DESIGN.md`, "The first runs with the walk").
         */
        const val LENGTH_SLACK = 1.5

        /**
         * The longest any note needs to sound, however long it is written at however slow a tempo: about a
         * beginner's breath on a long note, so a whole note at 40 a minute does not hold the run until Stop.
         * Changed only from recorded sessions, a sweep of `maxNeedMs` (`DESIGN.md`, "Each note held for its length").
         */
        const val MAX_NEED_MS = 3000.0

        /**
         * Frames a note's time runs on past the last frame the tracker showed it: the frames a gap takes to be
         * seen ([NoteOnsetTracker.ONSET_GAP_FRAMES]), so a note's thinning end still counts. A rule of its own, so
         * a sweep of the tracker's gap moves note starts and not note lengths.
         */
        const val GRACE_FRAMES = NoteOnsetTracker.ONSET_GAP_FRAMES

        /**
         * The share of a note's own level its last frames must fall under for a note cut short to have faded away
         * (the air running out) rather than stopped: the recordings of 2026-11-12 and 13 had 42 cuts stopped while
         * strong and 3 faded. Changed only from recorded sessions.
         */
        const val FADE_RATIO = 0.85f

        /** The frames at a note's end that [FADE_RATIO] judges, and the fewest of its own before them to judge against. */
        const val FADE_FRAMES = 3

        /** Room for the sum of frame lengths to miss an exact beat by a rounding error. */
        private const val EPSILON_MS = 1e-6
    }
}
