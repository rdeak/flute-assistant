// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.tuner.NoteMath

/**
 * What a note earned in "Your turn": a star for the first try, a faint star when a wrong note was heard for it first
 * (or a written repeat the phone could not hear), and since 2026-11-14 ("feedback, not stars") a sign in the star's
 * place for the right fingering played off: [HIGH] too high, [LOW] too low, [SHORT] let go before it was long enough,
 * and since 2026-11-16 [LONG] held past its written length (a mark given and then changed, [TuneCheck.remark]).
 * A sign says more than the faint star, so a step that stumbled and was then played off keeps the sign.
 */
enum class StepMark { FIRST_TRY, RETRY, HIGH, LOW, SHORT, LONG }

/** What became of one heard note: it was the waited-on note (first try, or after a stumble), a tail of the last one, or wrong. */
enum class Verdict { RIGHT, RETRY, IGNORED, WRONG }

/**
 * Checks the child's tune note by note as the flute hears them. It waits on one [TuneNote] of
 * [tune] at a time ([index]; rests and breaths are passed over) and judges each note heard
 * against it, pitch only and in any octave (the same letter: a beginner overblows into the
 * octave above, and the detector itself speaks the lower octave at an onset). A right pitch
 * does not earn the step by itself: [ListenRun] times how long it sounds and calls [earn] once
 * it is long enough (since 2026-11-11), with a sign for the way it was played off when there is one,
 * [StepMark.HIGH], [StepMark.LOW] or, for a note let go too early, [StepMark.SHORT] (since 2026-11-14:
 * the run moves on); by the rule before 2026-11-14 a note cut short was [stumble]d and waited on:
 *
 * - **right**: the waited note began, [Verdict.RETRY] when a wrong note or a note cut short came
 *   for it first; [earn] then gives it [StepMark.FIRST_TRY] or [StepMark.RETRY] and moves to the
 *   next note, and the last note ends the check ([isDone]);
 * - **ignored**: the heard note is the letter of the step just judged, in any octave (the child
 *   is still holding it, a glitch split it, or its lower octave locked first and then popped up),
 *   unless that letter is also the one waited on, a written repeat, *and* the note was a fresh
 *   start after a gap: a repeat can only be played by stopping and starting again, so a step
 *   without a gap (the octave pop of the note just counted) never counts a repeat as played;
 * - also **ignored**: by the rule before 2026-11-14, any other note while the step waited on was
 *   begun and cut short ([judge]'s `begun`): the child went on, and the step waits to be played again;
 *   and since then the note just judged started again at its own pitch ([judge]'s `sameAsLast`);
 * - **wrong**: anything else; the place is kept, and the step is remembered as stumbled. One
 *   exception, [forgiven]: when the step waited on repeats the note just judged, was not heard
 *   itself, and the heard note is the one written after it, the repeat was slurred on to or
 *   skipped, which the phone cannot tell apart, so it gets [StepMark.RETRY] and the heard note is
 *   judged for the step after it (several repeats in a row the same way), rather than the run
 *   stopping on a note the phone may not be able to hear. A repeat begun and then cut short is
 *   waited on like any note cut short ([judge]'s `begun`).
 *
 * Since 2026-11-14 a heard note is a written one ([isNote]) when it is its letter, or the semitone above it locked
 * within [highCents] of it: the right fingering blown high, which the tracker's nearest semitone names as the next note
 * up (on the evening's runs a B 48 to 71 cents high read as C). Under the written note the semitone's own 50 cents
 * hold: an F sharp fingered for G sounds just under the G and is a wrong note. [highCents] 0 is the rule before.
 *
 * Waiting for the right note is what keeps every mark under the note it is about: a skipped or
 * extra note cannot shift the ones after it. Nothing here is timed: [ListenRun] is.
 * Pure Kotlin; [ListenRun] feeds it heard notes and reads [marks].
 */
class TuneCheck(private val tune: List<TuneItem>, private val highCents: Double = HIGH_CENTS) {

    /** The index in [tune] of the note waited on; null when there is nothing (left) to check. */
    var index: Int? = nextNoteFrom(0)
        private set

    private val earned = mutableMapOf<Int, StepMark>()

    /** What each judged step earned, by its index in [tune]; steps not yet reached, rests and breaths are absent. */
    val marks: Map<Int, StepMark> get() = earned

    val isDone: Boolean get() = index == null

    /** Whether a wrong note, or a note cut short, has been heard for the step waited on. */
    private var stumbled = false
    /** The written note of the step earned last; null before the first. */
    var previous: Int? = null
        private set
    /** The steps the last [judge] passed over as repeats the phone could not hear; empty when none. */
    var forgiven: List<Int> = emptyList()
        private set

    /**
     * Judges the note [heard] (MIDI) against the step waited on; [fresh] says it began after a
     * gap rather than by a step from the note sounding before it. A right one is not yet earned
     * ([earn]); repeats it forgives are, at once.
     *
     * [sameAsLast] says the note began again where the note just judged sounded with no other note between (a breath
     * dip, an octave pop: [ListenRun] knows the pitch): it is that note's tail whatever letter the tracker names it,
     * unless the step waited on is its written repeat (since 2026-11-14: a b blown high, dipping, is not the c after it).
     *
     * [begun] says the step waited on was already begun at its right pitch and cut short, which
     * [ListenRun] knows (its walk stands there). Then any other letter is ignored, not wrong: the
     * child went on, and the step waits to be played again (the user's "wait"). So the
     * note written after a repeat does not forgive it, and its own letter coming back by a step is
     * the child returning to it, not the tail of the note before, which ended when the repeat began.
     */
    fun judge(heard: Int, fresh: Boolean, begun: Boolean = false, lockHz: Double? = null, sameAsLast: Boolean = false): Verdict {
        forgiven = emptyList()
        var current = index ?: return Verdict.IGNORED
        var expected = (tune[current] as TuneNote).midi
        val last = previous
        // The note just judged started again at its own pitch ([ListenRun]'s to say): its tail, unless a repeat is waited on.
        if (sameAsLast && last != null && !sameLetter(expected, last)) return Verdict.IGNORED
        // The note just judged going on: its letter, or its letter blown high unless that is the letter waited on.
        val tail = last != null && (sameLetter(heard, last) || (isNote(heard, lockHz, last) && !sameLetter(heard, expected)))
        if (tail && !((fresh || begun) && isNote(heard, lockHz, expected))) {
            return Verdict.IGNORED
        }
        // Repeats of the note just judged that were not heard before the note after them: forgiven.
        if (!isNote(heard, lockHz, expected) && last != null && sameLetter(expected, last) && !begun) {
            val repeats = mutableListOf<Int>()
            var after: Int? = current
            while (after != null && sameLetter((tune[after] as TuneNote).midi, last)) {
                repeats += after
                after = nextNoteFrom(after + 1)
            }
            if (after != null && isNote(heard, lockHz, (tune[after] as TuneNote).midi)) {
                for (repeat in repeats) earned[repeat] = StepMark.RETRY
                forgiven = repeats
                stumbled = false
                current = after
                expected = (tune[current] as TuneNote).midi
                index = current
            }
        }
        if (!isNote(heard, lockHz, expected)) {
            if (begun) return Verdict.IGNORED
            stumbled = true
            return Verdict.WRONG
        }
        return if (stumbled) Verdict.RETRY else Verdict.RIGHT
    }

    /**
     * The step waited on was played long enough: it earns [StepMark.FIRST_TRY], or
     * [StepMark.RETRY] after a stumble, and the check moves to the next note. A [remark] ([StepMark.HIGH],
     * [StepMark.LOW], [StepMark.SHORT]: how the right note was played off, [ListenRun]'s to say) is the mark instead.
     */
    fun earn(remark: StepMark? = null): StepMark {
        val current = checkNotNull(index) { "no step is waited on" }
        val mark = remark ?: star
        earned[current] = mark
        previous = (tune[current] as TuneNote).midi
        stumbled = false
        index = nextNoteFrom(current + 1)
        return mark
    }

    /** The star the step waited on would earn now: [StepMark.RETRY] after a stumble, else [StepMark.FIRST_TRY]. */
    val star: StepMark get() = if (stumbled) StepMark.RETRY else StepMark.FIRST_TRY

    /**
     * The step at [index], already earned, is marked [mark] instead ([ListenRun] reads a note on after its mark: held
     * too long, or its whole pitch). A step with no mark yet is refused.
     */
    fun remark(index: Int, mark: StepMark) {
        require(index in earned) { "step $index has no mark to change" }
        earned[index] = mark
    }

    /** The step waited on was cut short: once it is played long enough it earns [StepMark.RETRY]. */
    fun stumble() {
        stumbled = true
    }

    private fun nextNoteFrom(start: Int): Int? =
        (start until tune.size).firstOrNull { tune[it] is TuneNote }

    /**
     * Whether a note heard as [heard] (the tracker's nearest semitone), locked at [lockHz], is the written note
     * [written]: its letter in any octave, or the semitone above it locked within [highCents] of it in the nearest
     * octave (the right fingering blown high). With no pitch known, the letter alone.
     */
    fun isNote(heard: Int, lockHz: Double?, written: Int): Boolean =
        sameLetter(heard, written) ||
            (highCents > 0 && lockHz != null && sameLetter(heard, written + 1) && centsFrom(lockHz, written) in 0.0..highCents)

    companion object {
        /**
         * How far above the written note the right fingering blown high may sound and still be that note: the 25
         * notes of 2026-11-14 that the nearest semitone named as the next note up locked 48 to 71 cents high, and no
         * wrong note of that evening lay between 75 and 100. The rule `highCents` (`ListenRun.Rules`).
         */
        const val HIGH_CENTS = 75.0

        /** Whether two MIDI notes are the same letter, in any octave. */
        fun sameLetter(a: Int, b: Int): Boolean = Math.floorMod(a, 12) == Math.floorMod(b, 12)

        /** Cents from the written note [written] to [hz] in the octave nearest to it, in (-600, 600]: sharp is positive. */
        fun centsFrom(hz: Double, written: Int): Double {
            val cents = NoteMath.centsBetween(hz, written)
            val near = cents - 1200.0 * Math.floor((cents + 600.0) / 1200.0) // in [-600, 600)
            return if (near == -600.0) 600.0 else near
        }
    }
}
