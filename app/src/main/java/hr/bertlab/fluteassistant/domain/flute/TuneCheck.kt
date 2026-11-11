// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

/** What a note earned in "Your turn": a star for the first try, a faint star when a wrong note was heard for it first. */
enum class StepMark { FIRST_TRY, RETRY }

/** What became of one heard note: it was the waited-on note (first try, or after a stumble), a tail of the last one, or wrong. */
enum class Verdict { RIGHT, RETRY, IGNORED, WRONG }

/**
 * Checks the child's tune note by note as the flute hears them. It waits on one [TuneNote] of
 * [tune] at a time ([index]; rests and breaths are passed over) and judges each note heard
 * against it, pitch only and in any octave (the same letter: a beginner overblows into the
 * octave above, and the detector itself speaks the lower octave at an onset):
 *
 * - **right**: the step earns [StepMark.FIRST_TRY], or [StepMark.RETRY] when a wrong note was
 *   heard for it first, and the check moves to the next note; the last note ends it ([isDone]);
 * - **ignored**: the heard note is the letter of the step just judged, in any octave (the child
 *   is still holding it, a glitch split it, or its lower octave locked first and then popped up),
 *   unless that letter is also the one waited on, a written repeat, *and* the note was a fresh
 *   start after a gap: a repeat can only be played by stopping and starting again, so a step
 *   without a gap (the octave pop of the note just counted) never counts a repeat as played;
 * - **wrong**: anything else; the place is kept, and the step is remembered as stumbled. One
 *   exception, [forgiven]: when the step waited on repeats the note just judged and the heard
 *   note is the one written after it, the repeat was slurred on to or skipped, which the phone
 *   cannot tell apart, so it gets [StepMark.RETRY] and the heard note is judged for the step
 *   after it (several repeats in a row the same way), rather than the run stopping on a note the
 *   phone may not be able to hear.
 *
 * Waiting for the right note is what keeps every mark under the note it is about: a skipped or
 * extra note cannot shift the ones after it. Nothing here is timed; the child sets the pace.
 * Pure Kotlin; the view model feeds it heard notes and reads [marks].
 */
class TuneCheck(private val tune: List<TuneItem>) {

    /** The index in [tune] of the note waited on; null when there is nothing (left) to check. */
    var index: Int? = nextNoteFrom(0)
        private set

    private val earned = mutableMapOf<Int, StepMark>()

    /** What each judged step earned, by its index in [tune]; steps not yet reached, rests and breaths are absent. */
    val marks: Map<Int, StepMark> get() = earned

    val isDone: Boolean get() = index == null

    /** Whether a wrong note has been heard for the step waited on. */
    private var stumbled = false
    /** The written note of the step judged right last; null before the first. */
    var previous: Int? = null
        private set
    /** The steps the last [judge] passed over as repeats the phone could not hear; empty when none. */
    var forgiven: List<Int> = emptyList()
        private set

    /**
     * Judges the note [heard] (MIDI) against the step waited on; [fresh] says it began after a
     * gap rather than by a step from the note sounding before it.
     */
    fun judge(heard: Int, fresh: Boolean): Verdict {
        forgiven = emptyList()
        var current = index ?: return Verdict.IGNORED
        var expected = (tune[current] as TuneNote).midi
        val last = previous
        if (last != null && sameLetter(heard, last) && !(fresh && sameLetter(heard, expected))) {
            return Verdict.IGNORED
        }
        // Repeats of the note just judged that were not heard before the note after them: forgiven.
        if (!sameLetter(heard, expected) && last != null && sameLetter(expected, last)) {
            val repeats = mutableListOf<Int>()
            var after: Int? = current
            while (after != null && sameLetter((tune[after] as TuneNote).midi, last)) {
                repeats += after
                after = nextNoteFrom(after + 1)
            }
            if (after != null && sameLetter(heard, (tune[after] as TuneNote).midi)) {
                for (repeat in repeats) earned[repeat] = StepMark.RETRY
                forgiven = repeats
                stumbled = false
                current = after
                expected = (tune[current] as TuneNote).midi
                index = current
            }
        }
        if (!sameLetter(heard, expected)) {
            stumbled = true
            return Verdict.WRONG
        }
        val mark = if (stumbled) StepMark.RETRY else StepMark.FIRST_TRY
        earned[current] = mark
        previous = expected
        stumbled = false
        index = nextNoteFrom(current + 1)
        return if (mark == StepMark.RETRY) Verdict.RETRY else Verdict.RIGHT
    }

    private fun nextNoteFrom(start: Int): Int? =
        (start until tune.size).firstOrNull { tune[it] is TuneNote }

    companion object {
        /** Whether two MIDI notes are the same letter, in any octave. */
        fun sameLetter(a: Int, b: Int): Boolean = Math.floorMod(a, 12) == Math.floorMod(b, 12)
    }
}
