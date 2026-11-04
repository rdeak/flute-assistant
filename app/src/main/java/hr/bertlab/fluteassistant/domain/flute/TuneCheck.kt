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
 *   unless that letter is also the one waited on, a written repeat, which is right after
 *   [REPEAT_GUARD_MS] and ignored before it, so an octave pop never counts a repeat as played;
 * - **wrong**: anything else; the place is kept, and the step is remembered as stumbled.
 *
 * Waiting for the right note is what keeps every mark under the note it is about: a skipped or
 * extra note cannot shift the ones after it. Nothing here is timed but the repeat guard; the
 * child sets the pace. Pure Kotlin; the view model feeds it heard notes and reads [marks].
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
    /** The written note of the step judged right last, and when. */
    private var previous: Int? = null
    private var judgedAt = 0L

    /** Judges the note [heard] (MIDI) at [nowMs] against the step waited on. */
    fun judge(heard: Int, nowMs: Long): Verdict {
        val current = index ?: return Verdict.IGNORED
        val expected = (tune[current] as TuneNote).midi
        val last = previous
        if (last != null && sameLetter(heard, last) && (!sameLetter(heard, expected) || nowMs - judgedAt < REPEAT_GUARD_MS)) {
            return Verdict.IGNORED
        }
        if (!sameLetter(heard, expected)) {
            stumbled = true
            return Verdict.WRONG
        }
        val mark = if (stumbled) StepMark.RETRY else StepMark.FIRST_TRY
        earned[current] = mark
        previous = expected
        judgedAt = nowMs
        stumbled = false
        index = nextNoteFrom(current + 1)
        return if (mark == StepMark.RETRY) Verdict.RETRY else Verdict.RIGHT
    }

    private fun nextNoteFrom(start: Int): Int? =
        (start until tune.size).firstOrNull { tune[it] is TuneNote }

    private fun sameLetter(a: Int, b: Int): Boolean = Math.floorMod(a, 12) == Math.floorMod(b, 12)

    companion object {
        /**
         * A new note of the letter just judged within this long of the judgement is ignored even
         * when a repeat is written: a note that locks in its lower octave and pops up a few frames
         * later must not count its own repeat. A child's deliberate repeat is slower than this.
         */
        const val REPEAT_GUARD_MS = 300L
    }
}
