package hr.bertlab.fluteassistant.ui.tuner

/**
 * Notices sound that keeps coming but never becomes a note, so the tuner can tell the child
 * why (see [Hearing]). Pure Kotlin: [TunerViewModel] feeds it one frame at a time.
 *
 * A frame that is neither [FrameDecision.SHOWN] nor [FrameDecision.IGNORED] and whose level is
 * at least [soundLevel] is a sound frame and joins the run. A shown frame, or [reset], ends the
 * run at once. Frames under [soundLevel] are tolerated inside the run (a beginner breathes
 * between short notes) until [gapFrames] of them come in a row, which also ends it; once the
 * advice shows, the run lasts through up to [holdFrames] of them instead (about two seconds), and
 * sound after such a pause goes on with the same run and the same advice at once, so the line does
 * not blink off and wait another second each time the child breathes. Once the run holds
 * [unclearFrames] sound frames (about a second) [hearing] becomes [Hearing.QUIET_UNCLEAR] when the
 * mean level of the last [unclearFrames] sound frames is under [closeLevel], else
 * [Hearing.LOUD_UNCLEAR], re-evaluated on every sound frame. That sliding window, not the whole
 * run, is what makes moving the phone closer noticed within a second. Once one of the two is shown,
 * it changes to the other only when the mean is [closeMargin] past [closeLevel] (about 3 dB either
 * way), so a sound hovering at the boundary keeps the line it has instead of flipping between
 * "come closer" and "play one clear note" (seen on the user's phone on 2026-11-04).
 *
 * Both levels are starting values, to be confirmed from `levelMean` in `tuner.summary` on a
 * real phone (the emulator has no microphone).
 */
class HearingTracker(
    private val soundLevel: Float = SOUND_LEVEL,
    private val closeLevel: Float = CLOSE_LEVEL,
    private val closeMargin: Float = CLOSE_MARGIN,
    private val unclearFrames: Int = UNCLEAR_FRAMES,
    private val gapFrames: Int = UNCLEAR_GAP_FRAMES,
    private val holdFrames: Int = ADVICE_HOLD_FRAMES,
) {
    /** The levels of the last [unclearFrames] sound frames of the run; empty when there is no run. */
    private val levels = ArrayDeque<Float>()
    private var gap = 0

    var hearing: Hearing = Hearing.CLEAR
        private set

    /** Mean level of the sound frames in the window, `0.0` when there is no run. */
    val levelMean: Double
        get() = if (levels.isEmpty()) 0.0 else levels.sumOf { it.toDouble() } / levels.size

    /** Folds one frame in and returns the resulting [hearing]. */
    fun onFrame(level: Float, decision: FrameDecision): Hearing {
        when {
            decision == FrameDecision.IGNORED -> Unit
            decision == FrameDecision.SHOWN -> reset()
            level >= soundLevel -> {
                gap = 0
                levels.addLast(level)
                if (levels.size > unclearFrames) levels.removeFirst()
                if (levels.size == unclearFrames) hearing = judge(levelMean)
            }
            levels.isNotEmpty() && ++gap >= (if (hearing == Hearing.CLEAR) gapFrames else holdFrames) -> reset()
        }
        return hearing
    }

    /** Quiet or loud for a window whose mean level is [mean], keeping the one already shown until the mean is [closeMargin] past [closeLevel]. */
    private fun judge(mean: Double): Hearing = when (hearing) {
        Hearing.QUIET_UNCLEAR -> if (mean >= closeLevel + closeMargin) Hearing.LOUD_UNCLEAR else Hearing.QUIET_UNCLEAR
        Hearing.LOUD_UNCLEAR -> if (mean < closeLevel - closeMargin) Hearing.QUIET_UNCLEAR else Hearing.LOUD_UNCLEAR
        Hearing.CLEAR -> if (mean < closeLevel) Hearing.QUIET_UNCLEAR else Hearing.LOUD_UNCLEAR
    }

    /** Ends the run: back to [Hearing.CLEAR] with an empty window. */
    fun reset() {
        levels.clear()
        gap = 0
        hearing = Hearing.CLEAR
    }

    companion object {
        /**
         * The level (0..1, as in [hr.bertlab.fluteassistant.audio.tuner.PitchFrame.level]) from
         * which a frame counts as sound rather than room noise: about -45 dBFS. A starting value
         * to be confirmed from `levelMean` in `tuner.summary` on a real phone.
         */
        const val SOUND_LEVEL = 0.25f
        /**
         * The mean level from which the sound counts as close enough to the phone: about -30 dBFS.
         * Below it unclear sound means "come closer", from it "play one clear note". A starting
         * value to be confirmed from `levelMean` in `tuner.summary` on a real phone.
         */
        const val CLOSE_LEVEL = 0.5f
        /**
         * How far past [CLOSE_LEVEL] the mean must go before a shown "come closer" becomes "play one
         * clear note" or back: 0.05, about 3 dB on the level's scale (0.25 is about -45 dBFS, 0.5
         * about -30), more than a breathy tone's wobble from one second to the next.
         */
        const val CLOSE_MARGIN = 0.05f
        /** Sound frames in a run (and in the window judged for loudness) before it is reported: ~1 s at 43 ms per frame. */
        const val UNCLEAR_FRAMES = 25
        /** Consecutive frames under [SOUND_LEVEL] that end a run: ~0.4 s, longer than a breath between notes. */
        const val UNCLEAR_GAP_FRAMES = 10
        /**
         * Once the advice shows, consecutive frames under [SOUND_LEVEL] that end the run: ~2 s at 43 ms per frame,
         * so a child breathing between two unclear notes keeps the line instead of seeing it blink.
         */
        const val ADVICE_HOLD_FRAMES = 46
    }
}
