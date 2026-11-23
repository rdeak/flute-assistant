// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.domain.metronome.tempoBand

/**
 * A wrong note heard in "Your turn": [midi] came out for the step at [index] of the tune. Since 2026-11-14 the right
 * fingering blown high is not a wrong note ([TuneCheck.isNote]), so a wrong note is the fingers'.
 */
data class WrongNote(val index: Int, val midi: Int)

/**
 * Which way the right note sounds off its written pitch, further than [ListenRun.Rules.tuneCents]; null is in tune.
 * [mark] is the arrow a note earns for sounding that way.
 */
enum class Pitch(val mark: StepMark) { HIGH(StepMark.HIGH), LOW(StepMark.LOW) }

/**
 * The child's own speed over a finished "Your turn" (since 2026-11-14): [bpm], beats a minute from the median time
 * between the starts of [pairs] notes in a row (per written beat, rests included), against the knob's [knobBpm].
 * [speaks] when it is enough to say: [ListenRun.Rules.paceMinPairs] pairs, another animal than the knob's, and more
 * than [ListenRun.Rules.paceOff] off it.
 */
data class Pace(val bpm: Int, val pairs: Int, val knobBpm: Int, val speaks: Boolean) {
    /** The animal of the child's speed. */
    val child: TempoBand get() = tempoBand(bpm)

    /** The animal of the knob. */
    val knob: TempoBand get() = tempoBand(knobBpm)

    val slower: Boolean get() = bpm < knobBpm
}

/**
 * Where the knob's animal is in "Your turn" when it is not simply waiting at the head of the step waited
 * on: on the step at [index], [halfBeat] half beats into it (the place of a click inside it, as in Play),
 * and why ([phase]).
 */
data class Walk(val index: Int, val halfBeat: Int, val phase: Phase) {
    enum class Phase {
        /** The step's right note sounds and is not long enough yet: the animal walks it on the clicks. */
        SOUNDING,

        /**
         * The note stopped before it was long enough: the animal stays where it got to until it is played again (the
         * rule until 2026-11-14, [ListenRun.Rules.shortMovesOn] off).
         */
        SHORT,

        /**
         * The note stopped before it was long enough and is marked [StepMark.SHORT] (since 2026-11-14): the animal
         * stands where it got to for the rest of the note's written time, then goes on as after a note long enough.
         */
        CUT,

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
 * @property pitch which way the note walked sounds off its written pitch while it is not yet long enough (since
 * 2026-11-14): the arrow beside its bubble and the tuner's line; null in tune, and once it is long enough.
 * @property remark the sign the step marked last earned ([StepMark.HIGH], [StepMark.LOW], [StepMark.SHORT], and since
 * 2026-11-16 [StepMark.LONG]), whose line stays until the next note is judged,
 * right or wrong, so there is time to read it; null when it earned a star.
 * @property pace the child's speed, once the run is over, when it was another animal's than the knob's ([Pace.speaks]):
 * the one line the ended run's screen shows until it is closed.
 * @property tongued the note judged last was written joined to a note of another letter and started with the tongue
 * (since 2026-11-16, [ListenRun.Rules.slurCheck]): "✂️ Join them, no tongue" until the next note is judged.
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
    val pitch: Pitch? = null,
    val remark: StepMark? = null,
    val pace: Pace? = null,
    val tongued: Boolean = false,
) {
    /** The step the animal stands on: the one it walks, else the one waited on. */
    val animalIndex: Int? get() = walk?.index ?: index

    /** Half beats into [animalIndex] the animal stands: where its walk got to, else the step's head. */
    val animalHalfBeat: Int get() = walk?.halfBeat ?: 0

    /** The run is over and its screen stands, with the line about the child's speed ([pace]), until it is stopped. */
    val ended: Boolean get() = pace != null

    /**
     * Whether the animal waits for the child to play the step it stands over: not finishing, not on an earned note
     * finishing its time (its star shows there), not on a note let go too early standing out its time (its hourglass
     * shows there), not walking a rest (nothing is played there), and not on the ended run's screen.
     */
    val waiting: Boolean
        get() = !finishing && index != null && !ended && when (walk?.phase) {
            null, Walk.Phase.SOUNDING, Walk.Phase.SHORT -> true
            Walk.Phase.DONE, Walk.Phase.REST, Walk.Phase.CUT -> false
        }

    /**
     * The one line under the staff: stop and play it again when a written repeat is held on
     * instead, else come closer or play one clear note when the sound stays unclear, else let go
     * sooner for the step marked last held too long (since 2026-11-16), else a little lower or
     * higher while the note walked sounds off its pitch (since 2026-11-14, the tuner's own words),
     * else join them, no tongue, for a note written joined and started with the tongue ([tongued],
     * since 2026-11-16), else a little lower or higher after the step marked last earned an arrow,
     * else hold it longer while a note
     * cut short waits to be played again (since 2026-11-11), else check the fingers while a wrong
     * note stands (since 2026-11-10: the heard note said what came out, and nothing said what to do
     * about it), else nothing. One line is a guess and says so ("maybe"): out of air for a note cut
     * short that had [faded]. A note cut short and a wrong note never stand together: a note heard
     * while one is cut short is not a wrong note.
     */
    val advice: TunerAdvice
        get() = when {
            repeatHeld -> TunerAdvice.PLAY_AGAIN
            hearing == Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
            hearing == Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
            remark == StepMark.LONG -> TunerAdvice.TOO_LONG
            // The note walked sounding off its pitch first, so the tuning line is not silenced; then a joined note
            // tongued (since 2026-11-16), whose line outranks the arrow a step earned, as its mark does.
            pitch == Pitch.HIGH -> TunerAdvice.TOO_HIGH
            pitch == Pitch.LOW -> TunerAdvice.TOO_LOW
            tongued -> TunerAdvice.JOIN
            remark == StepMark.HIGH -> TunerAdvice.TOO_HIGH
            remark == StepMark.LOW -> TunerAdvice.TOO_LOW
            walk?.phase == Walk.Phase.SHORT || remark == StepMark.SHORT ->
                if (faded) TunerAdvice.OUT_OF_AIR else TunerAdvice.HOLD_LONGER
            wrong != null -> TunerAdvice.CHECK_FINGERS
            else -> TunerAdvice.NONE
        }
}
