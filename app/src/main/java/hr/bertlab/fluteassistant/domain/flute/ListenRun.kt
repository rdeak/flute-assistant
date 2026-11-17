// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.metronome.tempoBand
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.roundToLong

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
 * on, [Rules.restartCuts]; its own letter blown high too, [TuneCheck.isNote]) is let go too early: it is marked
 * [StepMark.SHORT] and the run is on the next note, the animal standing where it got to for the rest of the note's
 * written time ([Walk.Phase.CUT], [Rules.shortMovesOn], since 2026-11-14; by the rule before, the run waited on it and it
 * earned a faint star once played long enough). A note's pitch is read while it is walked ([Listening.pitch]) and its
 * mark is the arrow at the moment it is long enough ([StepMark.HIGH], [StepMark.LOW]). An earned note's written time is finished on the clicks, sound or
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
 * Since 2026-11-16 a note's mark may change after it was given, while its sound goes on (its tail, [OpenNote]): it
 * becomes [StepMark.LONG] the moment its sound runs more than [Rules.longSlack] half beats past its written length (never
 * the tune's last note). The frame says so ([Step.changed], [ListenFrame.changed]). The levels of that day, Practice and
 * Exam, went on 2026-11-17 (the user: "keep just rules from practice and remove exam logic completely"); with them went
 * Exam's mark that followed the whole note.
 *
 * Since 2026-11-16 (legato, [Joins]) a tie, or a slur to the same letter in another octave, is one note: walked from
 * its first note's head across all of them on their clicks, its written length theirs together, and nothing after it
 * until after its last note (no rest, no next note, no ✋ for the tune's last).
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
     * @property highCents how far above the written note its semitone above may lock and still be the written note
     * blown high ([TuneCheck.HIGH_CENTS], since 2026-11-14; 0 is the rule before: the nearest semitone).
     * @property tuneCents how far off its written pitch the note walked may sound and still be in tune ([TUNE_CENTS],
     * since 2026-11-14; infinite is the rule before: no arrow).
     * @property tuneFrames [TUNE_FRAMES].
     * @property shortMovesOn whether a note let go before it is long enough is marked [StepMark.SHORT] and the run moves
     * on ([Walk.Phase.CUT]; 1 in a session's line, since 2026-11-14); before, the run waited on it ([Walk.Phase.SHORT]).
     * @property paceMinPairs [PACE_MIN_PAIRS].
     * @property paceOff [PACE_OFF].
     * @property longSlack half beats past its written length a note may sound before it is held too long and marked
     * [StepMark.LONG] ([LONG_SLACK], since 2026-11-16; infinite is the rule before: nothing was too long).
     * @property slurCheck whether a note written joined to a note of another letter and started with the tongue is marked
     * [StepMark.TONGUED] (1 in a session's line, since 2026-11-16, legato); off, it earns its mark as any note.
     * @property slurStopDrop how deep a stop before such a note must fall, on the level scale, to be a tongue
     * ([SLUR_STOP_DROP]); a gap always is one.
     */
    data class Rules(
        val lengthSlack: Double = LENGTH_SLACK,
        val maxNeedMs: Double = MAX_NEED_MS,
        val repeatAdviceFrames: Int = REPEAT_ADVICE_FRAMES,
        val finishFrames: Int = FINISH_FRAMES,
        val restartCuts: Boolean = false,
        val graceFrames: Int = GRACE_FRAMES,
        val walkRests: Boolean = true,
        val highCents: Double = TuneCheck.HIGH_CENTS,
        val tuneCents: Double = TUNE_CENTS,
        val tuneFrames: Int = TUNE_FRAMES,
        val shortMovesOn: Boolean = true,
        val paceMinPairs: Int = PACE_MIN_PAIRS,
        val paceOff: Double = PACE_OFF,
        val longSlack: Double = LONG_SLACK,
        val slurCheck: Boolean = true,
        val slurStopDrop: Double = SLUR_STOP_DROP,
    ) {
        /** Name and value of every rule, in the order a session's `session.rules` line has them after the tracker's. */
        fun fields(): List<Pair<String, String>> = listOf(
            "repeatAdviceFrames" to repeatAdviceFrames.toString(), "finishFrames" to finishFrames.toString(),
            "lengthSlack" to lengthSlack.toString(), "maxNeedMs" to maxNeedMs.toString(),
            "restartCuts" to if (restartCuts) "1" else "0", "graceFrames" to graceFrames.toString(),
            "walkRests" to if (walkRests) "1" else "0",
            "highCents" to highCents.toString(), "tuneCents" to tuneCents.toString(), "tuneFrames" to tuneFrames.toString(),
            "shortMovesOn" to if (shortMovesOn) "1" else "0",
            "paceMinPairs" to paceMinPairs.toString(), "paceOff" to paceOff.toString(),
            "longSlack" to longSlack.toString(),
            "slurCheck" to if (slurCheck) "1" else "0", "slurStopDrop" to slurStopDrop.toString(),
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
            "highCents" -> copy(highCents = value)
            "tuneCents" -> copy(tuneCents = value)
            "tuneFrames" -> copy(tuneFrames = Math.round(value).toInt())
            "shortMovesOn" -> copy(shortMovesOn = value != 0.0)
            "paceMinPairs" -> copy(paceMinPairs = Math.round(value).toInt())
            "paceOff" -> copy(paceOff = value)
            "longSlack" -> copy(longSlack = value)
            "slurCheck" -> copy(slurCheck = value != 0.0)
            "slurStopDrop" -> copy(slurStopDrop = value)
            else -> throw IllegalArgumentException("no rule named $name")
        }

        companion object {
            /** The names [with] knows. */
            val NAMES: Set<String> = Rules().fields().map { it.first }.toSet()

            /**
             * No length rule: no slack to run out of, no longest hold and no note too long, so every note is long
             * enough the moment it is heard. The verdicts and marks are those of before 2026-11-11 (the animal still
             * finishes each note's written time, which a replay of such a session does not compare).
             */
            val PITCH_ONLY = Rules(
                lengthSlack = Double.POSITIVE_INFINITY,
                maxNeedMs = Double.POSITIVE_INFINITY,
                longSlack = Double.POSITIVE_INFINITY,
            )
        }
    }

    /**
     * A note that was judged: the step it was judged for, that step's written note, the note that began and what it
     * earned; [stopDrop], how deep the stop before it fell when it came by a restart ([NoteOnsetTracker.Result.stopDrop],
     * since 2026-11-16: what decides ✂️, logged so a mark on a phone can be read back).
     */
    data class Heard(val index: Int, val expected: Int, val onset: NoteOnsetTracker.Onset, val verdict: Verdict, val stopDrop: Float? = null)

    /**
     * How long the note of the step at [index] sounded ([heldMs]) against what it needed ([needMs]); [faded]: cut short, it
     * had faded away. On a note long enough, the [mark] it earned and [cents], the median of its readings off the written
     * pitch so far (where it locked when it has none yet).
     */
    data class NoteTime(
        val index: Int,
        val heldMs: Long,
        val needMs: Long,
        val faded: Boolean = false,
        val mark: StepMark? = null,
        val cents: Double? = null,
    )

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
     * @property short a note was cut short on this frame; its [NoteTime.mark] is [StepMark.SHORT] when the run moved on
     * ([Rules.shortMovesOn]), null when it waits on it.
     * @property held a note became long enough on this frame: it is earned and its mark shows.
     * @property cents the pitch the judged note locked at, in cents from [Heard.expected] (the step waited on when it
     * began); null when none was judged.
     * @property pace on the frame the last note is marked, the child's speed over the run, whether it speaks or not; null
     * on every other frame, and when no two notes in a row began clean.
     * @property changed a mark already given changed on this frame (since 2026-11-16): held too long.
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
        val pace: Pace? = null,
        val changed: Changed? = null,
    ) {
        /** A step was marked on this frame: earned, or let go too early and moved on from. */
        val advanced: Boolean get() = held != null || short?.mark != null

        /** The marks are not what they were before this frame: a step was marked, a mark given changed, or a repeat was forgiven. */
        val marksChanged: Boolean get() = advanced || changed != null || forgiven.isNotEmpty()
    }

    /**
     * The mark of the step at [index], already given, became [mark] on this frame (since 2026-11-16): the note sounded
     * [soundedMs] as far as the phone is sure of it, against its written [writtenMs]; [cents] the median of its readings
     * off the written pitch so far, null when it has none.
     */
    data class Changed(val index: Int, val mark: StepMark, val soundedMs: Long, val writtenMs: Long, val cents: Double?)

    /** What the walk did on one frame, gathered for its [Step]. */
    private class Moves {
        var short: NoteTime? = null
        var held: NoteTime? = null
        var changed: Changed? = null
    }

    /**
     * The note just earned while its sound goes on (since 2026-11-16): its step, where it began and the last frame the
     * tracker showed it on the run's clock, its readings off the written pitch, where it sounded when it was earned
     * ([sounded], a MIDI number with its cents; null when not known), and whether it was marked too long. Its own
     * times, kept apart from [time], which a rest or the next note replaces while it sounds.
     */
    private class OpenNote(
        val index: Int,
        val startMs: Double,
        var sureMs: Double,
        val readings: MutableList<Double>,
        val sounded: Double?,
    ) {
        var long = false
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
        /** Cents off the written pitch of every frame walked that the tracker showed, in order. */
        val readings = mutableListOf<Double>()
        /** Where the note locked, in cents off the written pitch; null when not known. */
        var lockCents: Double? = null
        /** The other side the last readings gave, and how many in a row ([Rules.tuneFrames] move the arrow there). */
        var pending: Pitch? = null
        var pendingCount = 0

        /** How far off its written pitch the note sounds, in cents: the median of its readings, or where it locked while it has none. */
        fun cents(): Double? = medianOrNull(readings) ?: lockCents
    }

    /** When the note of a step began on the run's clock, and whether it began [clean]: with no wrong note heard for it. */
    private class Start(val ms: Double, val clean: Boolean)

    private val check = TuneCheck(tune, run.highCents)
    private val tracker = NoteOnsetTracker(rules, floor)
    private val hearingTracker = HearingTracker()
    /** Frames since the last note was counted while its sound goes on. */
    private var framesFinishing = 0
    /**
     * Each step's clicks after its start, in half beats, where Play puts them; for notes heard as one (a tie, an octave
     * slur), the head has every click of them all, each counted from the head's start, a later note's own first click too.
     */
    private val clicks: Map<Int, List<Int>> = Tune.playSteps(tune, pulse).associateBy { it.index }.let { steps ->
        steps.mapValues { (head, _) ->
            var offset = 0
            (head..lastOf(head)).flatMap { index ->
                val own = steps.getValue(index)
                val at = listOfNotNull(offset.takeIf { index > head && own.clickAtStart }) +
                    own.inside.mapNotNull { it.halfBeat?.plus(offset) }
                offset += halfBeatsOf(index)
                at
            }
        }
    }
    /** The run's own clock: the frames' lengths added up, in ms. */
    private var nowMs = 0.0
    /** The note walked's time; replaced when a walk starts. */
    private var time = WalkTime(0.0)
    /** Frames since the animal finished an earned note's written time: what a written repeat held on is counted from. */
    private var pastLengthFrames = 0
    /**
     * The note just earned while it is the one sounding: no other note was judged since and the sound has not stopped;
     * null otherwise. Its tail may sound as another letter (a b blown high is c to the tracker), which is still not
     * what the next step hears.
     */
    private var open: OpenNote? = null
    /** When each step's note began, the last time it was played, by its step in the tune's order. */
    private val starts = sortedMapOf<Int, Start>()
    /** The child's speed, measured when the last note is marked. */
    private var pace: Pace? = null

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
        val moves = Moves()
        val result = tracker.onFrame(frame)
        if (tracker.sounding == null) closeTail()
        val hearingMean = hear(frame.level, result.decision)
        // The frame's pitch when the tracker shows it as a note; a frame it only holds a note through has none to read.
        val shownHz = frame.frequency.takeIf { result.decision == FrameDecision.SHOWN }
        val wasFinishing = listening.finishing
        val waited = check.index
        val stoodOn = place
        var onset = result.onset
        when (listening.walk?.phase) {
            Walk.Phase.SOUNDING -> if (goesOn(onset, result.lockHz)) {
                onset = null // an octave pop of the note walked, or its own letter again with no gap: the same note going on
                walkOn(frame.level, shownHz, graceMs = run.graceFrames * frame.hopMs, moves)
            } else {
                cutShort(moves)
            }
            Walk.Phase.DONE, Walk.Phase.REST, Walk.Phase.CUT -> writtenTime()
            Walk.Phase.SHORT -> Unit
            null -> pastLengthFrames++
        }
        val heard = onset?.let { judge(it, frame, result, moves) }
        open?.takeIf { moves.held?.index != it.index }?.let { followTail(it, shownHz, moves) }
        if (wasFinishing && (tracker.sounding == null || ++framesFinishing >= run.finishFrames)) isOver = true
        // The run's end closes the last note; nothing is open after it.
        if (isOver) closeTail()
        if (!wasFinishing && !listening.finishing) followSound()
        val row = ListenFrame(
            frame = frame, decision = result.decision, sounding = tracker.sounding, onset = result.onset, floor = tracker.floor,
            verdict = heard?.verdict, waited = waited, expected = waited?.let(::noteAt),
            heardNow = listening.heardNow, advice = listening.advice, repeatHeld = listening.repeatHeld,
            finishing = listening.finishing, hearing = listening.hearing, walk = listening.walk, pitch = listening.pitch,
            changed = moves.changed?.let { MarkChange(it.index, it.mark) },
        )
        val done = (moves.held != null || moves.short?.mark != null) && check.isDone
        if (done) pace = measurePace()
        // The run is over: the speed line stands, if there is one, and the last note's line is done.
        if (isOver && !listening.ended) pace?.takeIf { it.speaks }?.let { listening = listening.copy(pace = it, remark = null) }
        return Step(
            row, heard, check.forgiven.takeIf { heard != null }.orEmpty(), hearingMean,
            done = done,
            // One hop a frame at most, and only to another place, whatever the walk did on the way there.
            hop = place != stoodOn,
            short = moves.short, held = moves.held,
            cents = heard?.let { h -> result.lockHz?.let { NoteMath.centsBetween(it, h.expected) } },
            pace = pace.takeIf { done },
            changed = moves.changed,
        )
    }

    /**
     * What the microphone makes of sound that yields no note ([Listening.hearing]), after this frame at [level]; the
     * mean level that decided it when it changed on this frame, else null.
     */
    private fun hear(level: Float, decision: FrameDecision): Double? {
        val runMean = hearingTracker.levelMean // read before onFrame, which empties the window when the run ends
        val hearing = hearingTracker.onFrame(level, decision)
        val changed = hearing != listening.hearing
        listening = listening.copy(hearing = hearing)
        if (!changed) return null
        return if (hearing == Hearing.CLEAR) runMean else hearingTracker.levelMean
    }

    /**
     * Whether the note walked goes on through this frame: the tracker still holds a note (its own,
     * or a neighbour it sagged into with no onset) and nothing new began but its own letter, by an
     * octave pop or, unless [Rules.restartCuts], started again with no gap (while the tracker sounds, a
     * fresh onset is that); since 2026-11-14 also its own letter blown high ([TuneCheck.isNote], locked at
     * [lockHz]), unless that is the letter of the note written next, which is then what was played. How much
     * of that is the note's time is [walkOn]'s to say.
     */
    private fun goesOn(onset: NoteOnsetTracker.Onset?, lockHz: Double?): Boolean {
        if (tracker.sounding == null) return false
        if (onset == null) return true
        if (onset.fresh && run.restartCuts) return false
        val index = listening.walk!!.index
        val written = noteAt(index)
        if (TuneCheck.sameLetter(onset.midi, written)) return true
        if (!check.isNote(onset.midi, lockHz, written)) return false
        // Its letter blown high: the note going on when it locked where the note sounds now (a pop, a dip), or when the
        // note written next is not that letter; a step to where the next note is written is that note.
        val next = noteAfter(index)
        return samePitch(lockHz, sounds(written)) || next == null || !TuneCheck.sameLetter(onset.midi, noteAt(next))
    }

    /** Where the note walked sounds now, as a MIDI number with its cents: its written note and the last readings' median. */
    private fun sounds(written: Int): Double? =
        (medianOrNull(time.readings.takeLast(ListenRules.SMOOTHING_WINDOW)) ?: time.lockCents)?.let { written + it / 100.0 }

    /** Whether [lockHz] lies within [ListenRules.LOCK_CENTS] of [midi] (a MIDI number with cents), in the nearest octave. */
    private fun samePitch(lockHz: Double?, midi: Double?): Boolean {
        if (lockHz == null || midi == null) return false
        val semitones = NoteMath.exactMidi(lockHz) - midi
        return Math.abs(semitones - 12 * Math.round(semitones / 12.0)) * 100 <= ListenRules.LOCK_CENTS
    }

    /** The step of the next note written after the step at [head] and the notes heard as one with it; null when none. */
    private fun noteAfter(head: Int): Int? = (lastOf(head) + 1 until tune.size).firstOrNull { tune[it] is TuneNote }

    /**
     * A new note began ([onset]) on [frame], as the tracker's [result] has it (where it locked, how many frames ago it
     * began, how deep the stop before it fell): judge it against the note waited on; null when nothing is waited on.
     */
    private fun judge(onset: NoteOnsetTracker.Onset, frame: PitchFrame, result: NoteOnsetTracker.Result, moves: Moves): Heard? {
        val index = check.index ?: return null
        val expected = noteAt(index)
        val lockHz = result.lockHz
        val stopDrop = result.stopDrop
        // A walk cut short stands on the step waited on: its note was begun, which the check must know.
        // The note just earned started again where it sounded (a breath dip, a pop) is still that note, whatever its letter.
        val sameAsLast = run.highCents > 0 && samePitch(lockHz, open?.sounded)
        val verdict = check.judge(onset.midi, onset.fresh, begun = listening.walk?.phase == Walk.Phase.SHORT, lockHz = lockHz, sameAsLast = sameAsLast)
        if (verdict != Verdict.IGNORED) {
            // Another note was judged: the last one's tail and its line are over.
            closeTail()
            listening = listening.copy(remark = null, tongued = false)
        }
        // A note written joined to another letter, found at once, that began after a gap or a stop as deep as a tongue
        // (since 2026-11-16): never after a wrong note, which already broke the join.
        val tongued = run.slurCheck && verdict == Verdict.RIGHT && Joins.joinsOtherLetter(tune, check.index!!) &&
            onset.fresh && (stopDrop == null || stopDrop >= run.slurStopDrop)
        when (verdict) {
            Verdict.RIGHT, Verdict.RETRY -> startWalk(frame, result, clean = verdict == Verdict.RIGHT, tongued, moves)
            Verdict.WRONG -> {
                // The note heard stays under the step until the next note is judged: no timer, so there
                // is time to read it. The animal leaves an earned note's last half beat for it at once.
                if (listening.walk != null) leaveWalk()
                listening = listening.copy(wrong = WrongNote(index, onset.midi))
            }
            // An ignored note (the last step's tail, an octave pop, or the child going on while a note cut short
            // waits) leaves everything as it was.
            Verdict.IGNORED -> Unit
        }
        return Heard(index, expected, onset, verdict, stopDrop)
    }

    /**
     * The right note for the step waited on began on [frame] ([result]: where it locked and how many frames ago), [clean]
     * when no wrong note was heard for it, [tongued] when written joined and started with the tongue: the animal walks
     * it from its head, as far as the note has sounded already (it hops there unless it stands there: never twice in
     * one place).
     */
    private fun startWalk(frame: PitchFrame, result: NoteOnsetTracker.Result, clean: Boolean, tongued: Boolean, moves: Moves) {
        val index = check.index!! // the heard note's step, after any repeats forgiven
        // The note began inside the first of its frames: that frame's hop counts in full, and the ones that locked it.
        time = WalkTime(startMs = nowMs - (result.leadFrames + 1) * frame.hopMs)
        starts[index] = Start(time.startMs, clean)
        time.lockCents = result.lockHz?.let { TuneCheck.centsFrom(it, noteAt(index)) }
        listening = listening.copy(
            index = index, walk = Walk(index, 0, Walk.Phase.SOUNDING), wrong = null, heardNow = null, repeatHeld = false, faded = false,
            tongued = tongued,
            pitch = time.lockCents?.let(::side), // the arrow starts where the note locked
        )
        walkOn(frame.level, frame.frequency, graceMs = 0.0, moves) // a note begins on a frame shown: no grace to add
    }

    /**
     * The note walked goes on through this frame, at [level]: shown by the tracker at the pitch [shownHz], or only held
     * by it (null), which counts for [graceMs] past the last frame shown at most. The animal steps to the click the note
     * reached, and the note is earned once it is long enough. A stretch the tracker only held (the gate blinking on a
     * breathy tone) counts in full once the note is shown again, and only a frame shown moves the arrow ([readPitch]).
     */
    private fun walkOn(level: Float, shownHz: Double?, graceMs: Double, moves: Moves) {
        time.levels += level
        if (shownHz != null) {
            time.sureMs = nowMs
            readPitch(TuneCheck.centsFrom(shownHz, noteAt(listening.walk!!.index)))
        }
        time.heldMs = minOf(nowMs, time.sureMs + graceMs) - time.startMs
        stepTo(time.heldMs)
        if (time.heldMs + EPSILON_MS >= needMs(listening.walk!!.index)) earn(moves)
    }

    /** The note walked is long enough: it earns its mark, and its tail is followed from here ([open]). */
    private fun earn(moves: Moves) {
        val index = listening.walk!!.index
        // The note's mark is the scissors for a joined note tongued (a length sign later says more), else the arrow its
        // bubble shows now: the way it sounded off, if it did.
        val remark = if (listening.tongued) StepMark.TONGUED else listening.pitch?.mark
        val mark = check.earn(remark)
        moves.held = NoteTime(index, time.heldMs.roundToLong(), needMs(index).roundToLong(), mark = mark, cents = time.cents())
        open = OpenNote(index, time.startMs, time.sureMs, time.readings.toMutableList(), sounded = sounds(noteAt(index)))
        listening = marked(Walk.Phase.DONE, remark)
    }

    /**
     * The step walked was just marked and the check moved on: what the screen shows from here. The run waits on the
     * next note (on this one still when it was the tune's last, the animal staying over it), the animal is in [phase]
     * where it stands, and [remark] is the sign whose line stays until the next note is judged.
     */
    private fun marked(phase: Walk.Phase, remark: StepMark?): Listening {
        val walk = listening.walk!!
        val done = check.isDone
        return listening.copy(
            index = if (done) walk.index else check.index,
            walk = walk.copy(phase = phase),
            // The last note marked ends the run while its sound ends; a rest after it is walked first, and ends the run.
            finishing = done && restAfter(walk.index) == null,
            wrong = null, heardNow = null, repeatHeld = false, pitch = null, remark = remark,
        )
    }

    /**
     * The note just earned goes on through this frame: a frame the tracker shows (at the pitch [shownHz]) moves the
     * note's sure time and adds a reading, and the moment that time runs more than [Rules.longSlack] half beats past the
     * note's written length its mark becomes [StepMark.LONG], with its line, once (since 2026-11-16). The tune's last
     * note is never too long: nothing comes after it to be late for.
     */
    private fun followTail(note: OpenNote, shownHz: Double?, moves: Moves) {
        if (shownHz != null) {
            note.sureMs = nowMs
            note.readings += TuneCheck.centsFrom(shownHz, noteAt(note.index))
        }
        val over = note.sureMs - note.startMs - fullMs(note.index)
        if (note.long || noteAfter(note.index) == null || over <= run.longSlack * pulse.halfBeatMs + EPSILON_MS) return
        note.long = true
        check.remark(note.index, StepMark.LONG)
        listening = listening.copy(remark = StepMark.LONG)
        moves.changed = Changed(
            note.index, StepMark.LONG, (note.sureMs - note.startMs).roundToLong(), fullMs(note.index).roundToLong(),
            medianOrNull(note.readings),
        )
    }

    /** The note just earned is over: its sound ended, another note was judged, or the run ended. */
    private fun closeTail() {
        open = null
    }

    /**
     * A reading of the note walked, [cents] off its written pitch: the arrow follows the median of the last
     * [ListenRules.SMOOTHING_WINDOW] readings over to another side ([side]) once [Rules.tuneFrames] of them in a row
     * gave that side, so a wobble does not flicker.
     */
    private fun readPitch(cents: Double) {
        time.readings += cents
        val now = side(median(time.readings.takeLast(ListenRules.SMOOTHING_WINDOW)))
        val shown = listening.pitch
        when {
            now == shown -> time.pendingCount = 0
            now == time.pending && time.pendingCount > 0 -> time.pendingCount++
            else -> {
                time.pending = now
                time.pendingCount = 1
            }
        }
        if (time.pendingCount >= run.tuneFrames) {
            listening = listening.copy(pitch = now)
            time.pendingCount = 0
        }
    }

    /** Which way [cents] off the written pitch is, beyond [Rules.tuneCents]; null in tune. */
    private fun side(cents: Double): Pitch? = when {
        cents > run.tuneCents -> Pitch.HIGH
        cents < -run.tuneCents -> Pitch.LOW
        else -> null
    }

    /**
     * The note walked stopped before it was long enough: it is marked [StepMark.SHORT] and the run is on the next note,
     * the animal standing where it got to for the rest of the note's written time ([Rules.shortMovesOn]); or, by the
     * rule before, the animal stays there and the step waits, to earn a faint star once played long enough.
     */
    private fun cutShort(moves: Moves) {
        val walk = listening.walk!!
        if (brokenSlur(walk.index)) return breakSlur(moves)
        // Only a note whose sound ended can have run out of air: one that went on into another note was still blown.
        val faded = tracker.sounding == null && faded(time.levels)
        val held = NoteTime(walk.index, time.heldMs.roundToLong(), needMs(walk.index).roundToLong(), faded)
        if (!run.shortMovesOn) {
            check.stumble()
            moves.short = held
            listening = listening.copy(walk = walk.copy(phase = Walk.Phase.SHORT), faded = faded, pitch = null)
            return
        }
        val mark = check.earn(StepMark.SHORT)
        moves.short = held.copy(mark = mark)
        // A last note let go too early ends the run as one long enough does.
        listening = marked(Walk.Phase.CUT, mark).copy(faded = faded)
    }

    /**
     * Whether the walk on [head], an octave slur heard as one note, was broken by a gap (the sound stopped) after its
     * first note had sounded its own length less the slack (since 2026-11-16, the final review): not a note let go too
     * early but a slur broken by a tongue, which a gap always is. A tie broken so is still cut short: one written sound.
     */
    private fun brokenSlur(head: Int): Boolean {
        val last = lastOf(head)
        return run.slurCheck && run.shortMovesOn && tracker.sounding == null && last > head &&
            (head + 1..last).any { noteAt(it) != noteAt(head) } && time.heldMs + EPSILON_MS >= ownNeedMs(head)
    }

    /**
     * The octave slur walked was broken by a gap ([brokenSlur]): its first note earns its own mark (the arrow it sounds
     * off by, or its star), the later ones ✂️ and the line says so; the animal finishes the slur's written time as after
     * a note long enough, and the run waits on the note after it (the slur's later note, played now, is its own tail).
     */
    private fun breakSlur(moves: Moves) {
        val index = listening.walk!!.index
        val mark = check.earnBroken(listening.pitch?.mark)
        moves.held = NoteTime(index, time.heldMs.roundToLong(), ownNeedMs(index).roundToLong(), mark = mark, cents = time.cents())
        listening = marked(Walk.Phase.DONE, StepMark.TONGUED).copy(tongued = true)
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

    /**
     * An earned note's written time, or a rest's, goes on on its clicks, sound or not (a note let go too early stands
     * where it got to, [Walk.Phase.CUT]); at its end the animal walks the rest after it, or goes to the next note's head.
     */
    private fun writtenTime() {
        val walk = listening.walk!!
        val since = nowMs - time.startMs
        if (walk.phase != Walk.Phase.CUT) stepTo(since)
        if (since + EPSILON_MS < fullMs(walk.index)) return
        val rest = restAfter(walk.index)
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

    /** The rest the animal walks after the step at [head] and the notes heard as one with it ([restFrom]); null when none. */
    private fun restAfter(head: Int): Int? = restFrom(lastOf(head) + 1)

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
     * many counted frames since it began (a slur back to it starts that again). Since 2026-11-14
     * ([Rules.highCents] over 0) the tail is also the note just earned sounding as another letter
     * ([open]: a b blown high is c to the tracker), and the bubble shows the written letter for the
     * right note blown off its pitch, the walked note's or the repeat's, never its neighbour's.
     */
    private fun followSound() {
        val expected = noteAt(check.index ?: return)
        val sounding = tracker.sounding
        if (sounding == null) { // silence: nothing is heard, and nothing is held on
            listening = listening.copy(heardNow = null, repeatHeld = false)
            return
        }
        val previous = check.previous
        val band = run.highCents > 0
        val isExpected = TuneCheck.sameLetter(sounding, expected)
        val ofPrevious = previous != null && (TuneCheck.sameLetter(sounding, previous) || (band && open != null))
        val ofExpected = isExpected || (ofPrevious && TuneCheck.sameLetter(previous!!, expected))
        val walking = listening.walk?.phase == Walk.Phase.SOUNDING
        val heardNow = when {
            // The note walked is the written note, whatever letter the tracker names it and whatever came before it.
            band && walking -> if (isExpected) sounding else expected
            ofPrevious && !ofExpected -> null
            band && ofExpected && !isExpected -> expected
            else -> sounding
        }
        listening = listening.copy(
            heardNow = heardNow,
            repeatHeld = ofPrevious && ofExpected && listening.walk == null &&
                pastLengthFrames >= run.repeatAdviceFrames && tracker.framesSinceOnset >= run.repeatAdviceFrames,
        )
    }

    private fun noteAt(index: Int): Int = (tune[index] as TuneNote).midi

    /**
     * The child's speed over the run, in the knob's clicks a minute: a pair is two notes in a row that each began (the
     * second clean, with no wrong note heard for it), its beat the time between their starts per click written from the
     * first head to the second, rests and breaths included; the speed is the median beat. Null when no pair counted.
     */
    private fun measurePace(): Pace? {
        val beats = starts.entries.zipWithNext().filter { (_, second) -> second.value.clean }.mapNotNull { (first, second) ->
            val halfBeats = (first.key until second.key).sumOf(::halfBeatsOf)
            // Per click of the knob, the beat its bpm counts: a quarter in x/4, an eighth in x/8, a half in x/2.
            if (halfBeats == 0) null else (second.value.ms - first.value.ms) / (halfBeats.toDouble() / pulse.halfBeatsPerClick)
        }
        if (beats.isEmpty()) return null
        val bpm = Math.round(60_000.0 / median(beats)).toInt()
        val knob = pulse.bpm
        val speaks = beats.size >= run.paceMinPairs && tempoBand(bpm) != tempoBand(knob) && Math.abs(bpm - knob) > run.paceOff * knob
        return Pace(bpm, beats.size, knob, speaks)
    }

    /** The written half beats of the step at [index]: with the notes heard as one with it, all of theirs. */
    private fun halfBeatsAt(index: Int): Int = (index..lastOf(index)).sumOf(::halfBeatsOf)

    private fun halfBeatsOf(index: Int): Int = tune[index].length?.halfBeats ?: 0

    /** The last step heard as one with the step at [index] ([Joins.lastHeardWith]): itself unless a tie or an octave slur goes on. */
    private fun lastOf(index: Int): Int = Joins.lastHeardWith(tune, index)

    /** How long the note at [index] must sound to be earned ([needFor]), with the notes heard as one with it. */
    private fun needMs(index: Int): Double = needFor(halfBeatsAt(index))

    /** How long the note at [index] alone must sound, not the notes heard as one with it. */
    private fun ownNeedMs(index: Int): Double = needFor(halfBeatsOf(index))

    /** How long a note written [halfBeats] long must sound: its length less the slack, never under nothing nor over the longest hold. */
    private fun needFor(halfBeats: Int): Double = minOf(maxOf(0.0, halfBeats - run.lengthSlack) * pulse.halfBeatMs, run.maxNeedMs)

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

        /**
         * Cents off its written pitch the note walked may sound and still be in tune (since 2026-11-14): on the evening's
         * 265 right fingerings 46% sat above it, 79 to 95% of the notes of the runs blown sharp and 0 to 4% of the ones in
         * tune; at 15 it was 70% of all notes, at 40 only 17%. Changed only from recorded sessions, a sweep of `tuneCents`.
         */
        const val TUNE_CENTS = 25.0

        /** Readings in a row on another side that move the arrow there: without it 22 of those 265 notes flickered. */
        const val TUNE_FRAMES = 3

        /** Pairs of notes in a row a run's speed needs before it is said: a guess from three notes is not said. */
        const val PACE_MIN_PAIRS = 6

        /**
         * How far off the knob's speed the child's must be, as a share of it, besides being another animal, before it is
         * said: a child at 78 with the knob on 80 is the turtle against the duck, and not slower.
         */
        const val PACE_OFF = 0.15

        /**
         * Half beats past its written length a note may sound before it is held too long, three quarters of
         * a beat, the room [LENGTH_SLACK] gives the other way (the user's ask of 2026-11-16: "on practice also check when
         * held is too long"). Changed only from recorded sessions, a sweep of `longSlack`.
         */
        const val LONG_SLACK = 1.5

        /**
         * How deep the stop before a note written joined must fall to be the tongue, on the level scale (1.0 is 60 dB):
         * 0.25, 15 dB. Deeper than a child's breath dip (about 10 dB, 0.17) and the tracker's own
         * `NoteOnsetTracker.STOP_DROP` (0.12, 7 dB), which hears both; shallower than a tongue in a dry room (the tongued
         * stops of 2026-11-05 fell 27 dB or more, `ui/flute/DESIGN.md`). A tongue in a ringing room may fall less and keep
         * its star: the safe side, since ✂️ on a note the child did join would be false. A gap is always a tongue. The rule
         * `slurStopDrop`; change it only from recorded sessions.
         */
        const val SLUR_STOP_DROP = 0.25

        /** Room for the sum of frame lengths to miss an exact beat by a rounding error. */
        private const val EPSILON_MS = 1e-6
    }
}

/** The median of [values], the mean of the middle two for an even count. */
private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

private fun medianOrNull(values: List<Double>): Double? = values.takeIf { it.isNotEmpty() }?.let(::median)
