package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.abs
import kotlin.math.log2

/**
 * Turns microphone frames into notes the way the tuner's "Which?" mode does, and says when a
 * *new* note begins, which is what the flute's "Your turn" judges. Pure Kotlin, one frame at a
 * time, with the tuner's own thresholds: a frame counts only with a frequency and a probability
 * of at least [TunerViewModel.MIN_PROBABILITY]; a note is sounding once the last
 * [TunerViewModel.LOCK_FRAMES] counted frames agree within [TunerViewModel.LOCK_CENTS]; the median
 * of [TunerViewModel.SMOOTHING_WINDOW] frames names it; a sound that has only drifted past the
 * halfway point to a neighbouring note keeps the old note until it has lasted
 * [TunerViewModel.SWITCH_FRAMES], while a clear step (further than
 * [TunerUiState.NEAR_TARGET_CENTS]) switches at once.
 *
 * A new note begins when a note sounds after a gap, or when the sounding note changes to another
 * by a clear step without a gap (a slur, a fingered change). A gap is [ONSET_GAP_FRAMES] frames in
 * a row that did not count *and* lost the note's loudness (under [GAP_LEVEL_RATIO] of the note's
 * mean level): silence, a breath, a tongued gap. A frame that lost its pitch but kept its loudness
 * is the probability gate blinking on a breathy tone, not a gap; counting it would split one held
 * note in two and count a written repeat as played. After a gap the window is emptied, so the next
 * note needs fresh agreeing frames. The same note going on is not a new note, so a repeated note
 * needs a fresh start; a slow drift past the halfway point to a neighbour (a long note sagging as
 * the breath runs out) moves the sounding note, as the tuner's ring would move, but is no new note
 * either: nobody played one. Unsteady frames do not count towards the gap: a wobble inside a held
 * note must not make it two.
 */
class NoteOnsetTracker(private val referenceA4: Double = NoteMath.DEFAULT_A4) {

    /** What became of one frame, and the MIDI note that began on it, if one did. */
    data class Result(val decision: FrameDecision, val onset: Int?)

    /** The note sounding now, as the tuner's "Which?" ring would show it; null between notes. */
    var sounding: Int? = null
        private set

    private val recent = ArrayDeque<Double>()
    /** Frames in a row that did not count and were quiet enough; [ONSET_GAP_FRAMES] of them end the sounding note. */
    private var gap = 0
    /** Shown frames in a row whose note is a neighbour of [sounding] within reach; see [identify]. */
    private var driftFrames = 0
    /** Whether the last [identify] moved [sounding] by a drift rather than a clear step or a fresh start. */
    private var drifted = false
    /** The mean level of the counted frames of the sounding note, which a gap must fall well under. */
    private var levelSum = 0.0
    private var levelCount = 0

    fun onFrame(frame: PitchFrame): Result {
        val frequency = frame.frequency ?: return uncounted(FrameDecision.SILENT, frame.level)
        if (frame.probability < TunerViewModel.MIN_PROBABILITY) return uncounted(FrameDecision.LOW_PROBABILITY, frame.level)
        gap = 0
        levelSum += frame.level
        levelCount++
        recent.addLast(frequency)
        while (recent.size > TunerViewModel.SMOOTHING_WINDOW) recent.removeFirst()
        if (!isSteady()) return Result(FrameDecision.UNSTEADY, onset = null)
        val median = recent.sorted()[recent.size / 2]
        val before = sounding
        val identified = identify(NoteMath.frequencyToNote(median, referenceA4).midi, median)
        val onset = identified.takeIf { it != before && !drifted }
        if (onset != null) {
            // The new note's loudness is its own; the frames that locked it are its first.
            levelSum = frame.level.toDouble()
            levelCount = 1
        }
        sounding = identified
        return Result(FrameDecision.SHOWN, onset)
    }

    /** Back to silence: no note sounding, nothing remembered. */
    fun reset() {
        sounding = null
        recent.clear()
        gap = 0
        driftFrames = 0
        drifted = false
        levelSum = 0.0
        levelCount = 0
    }

    /**
     * A frame that did not count. While a note sounds it counts towards the gap only when its
     * [level] fell well under the note's ([GAP_LEVEL_RATIO]); between notes every such frame counts.
     */
    private fun uncounted(decision: FrameDecision, level: Float): Result {
        val quiet = sounding == null || levelCount == 0 || level < (levelSum / levelCount) * GAP_LEVEL_RATIO
        if (quiet && ++gap >= ONSET_GAP_FRAMES) reset()
        return Result(decision, onset = null)
    }

    /** True once the last [TunerViewModel.LOCK_FRAMES] frames agree within [TunerViewModel.LOCK_CENTS]. */
    private fun isSteady(): Boolean {
        if (recent.size < TunerViewModel.LOCK_FRAMES) return false
        val last = recent.takeLast(TunerViewModel.LOCK_FRAMES)
        return 1200.0 * log2(last.max() / last.min()) <= TunerViewModel.LOCK_CENTS
    }

    /**
     * The note [heard] (the median's nearest note) becomes the sounding note at once when nothing
     * was sounding, when it is the sounding note, or when it is a clear step away; a drift that has
     * only passed the halfway point to a neighbour waits for [TunerViewModel.SWITCH_FRAMES] shown
     * frames in a row, one frame back on the sounding note starting the count again.
     */
    private fun identify(heard: Int, median: Double): Int {
        val current = sounding
        drifted = false
        if (current == null || heard == current) {
            driftFrames = 0
            return heard
        }
        val reach = abs(NoteMath.centsBetween(median, current, referenceA4))
        if (reach > TunerUiState.NEAR_TARGET_CENTS) {
            driftFrames = 0
            return heard
        }
        if (++driftFrames >= TunerViewModel.SWITCH_FRAMES) {
            driftFrames = 0
            drifted = true
            return heard
        }
        return current
    }

    companion object {
        /**
         * Quiet frames in a row that did not count (about 90 ms) after which the next note is a new
         * one. One frame is too few: a single dropped frame inside a held note would split it in two.
         */
        const val ONSET_GAP_FRAMES = 2

        /**
         * While a note sounds, an uncounted frame is quiet enough to count towards the gap only
         * under this share of the note's mean level (the level is the engine's 0..1 dB-style
         * scale, so half of a note at -20 dBFS is about -40 dBFS, a 20 dB drop). A tongued gap
         * falls to the room's noise; the gate blinking on a breathy tone keeps the note's level.
         * A starting value, to be read against `flute.heard` and `flute.listen_summary` on a phone.
         */
        const val GAP_LEVEL_RATIO = 0.5f
    }
}
