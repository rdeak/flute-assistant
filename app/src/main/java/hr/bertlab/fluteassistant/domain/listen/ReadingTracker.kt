// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.NoteReading
import kotlin.math.abs

/**
 * The tuner's reading rules: smooths raw [PitchFrame]s into the note the tuner shows.
 * - A reading shows only once a few consecutive frames agree (a held note, not room noise).
 * - A short median window keeps a single mis-detected frame from flicking the needle.
 * - Low-confidence frames are ignored ([ListenRules.MIN_PROBABILITY]).
 * - The last reading is held for a few silent or unsteady frames so the display does not blink between notes; a
 *   wandering pitch expires it after [ListenRules.HOLD_FRAMES] like silence does, and only a steady note resets
 *   that count.
 * - The identified note (the ring of the tuner's "Which?" mode) is kept through a drift just past the halfway point
 *   to a neighbouring note until the drift has lasted [ListenRules.SWITCH_FRAMES].
 *
 * The flute's [NoteOnsetTracker] has rules of its own on top of these (the room's floor, gaps, a loudness floor); the
 * tuner needs none of them, so the two share only the [SmoothingWindow]. Pure Kotlin.
 */
class ReadingTracker {
    /** The note shown, or null when nothing steady has been heard for a while. */
    var reading: NoteReading? = null
        private set

    /** The note the ring names while a [reading] shows; what it named last is kept through silence and [reset]. */
    var identifiedMidi: Int? = null
        private set

    private val window = SmoothingWindow()
    /** Frames in a row without a steady note (silent, uncertain or unsteady); [ListenRules.HOLD_FRAMES] of them clear the reading. */
    private var silentFrames = 0
    /** Shown frames in a row whose note is not the identified one but still within reach of it; see [identify]. */
    private var driftFrames = 0

    /** Folds one frame in and says what became of it. */
    fun onFrame(frame: PitchFrame): FrameDecision {
        val frequency = frame.frequency ?: return unpitched(FrameDecision.SILENT)
        if (frame.probability < ListenRules.MIN_PROBABILITY) return unpitched(FrameDecision.LOW_PROBABILITY)

        window.add(frequency)
        if (!window.isSteady()) {
            // Counts towards the hold like silence: a wandering pitch must not keep the last reading up for as long as
            // it lasts. Until it is steady the previous reading stays rather than jumping to the stray sound.
            silentFrames++
            if (silentFrames >= ListenRules.HOLD_FRAMES) reading = null
            return FrameDecision.UNSTEADY
        }
        silentFrames = 0
        val heard = NoteMath.frequencyToNote(window.median())
        identifiedMidi = identify(heard)
        reading = heard
        return FrameDecision.SHOWN
    }

    /** The listening starts afresh: nothing shown, nothing remembered of the sound before. */
    fun reset() {
        window.clear()
        silentFrames = 0
        reading = null
    }

    /**
     * The identified note once [heard] is shown. The first note after silence is taken at once, and so is a clearly
     * different note (further than [ListenRules.NEAR_CENTS] from the identified one, such as a clean step to the next
     * note). A sound that has only drifted past the halfway point to a neighbour switches the ring after
     * [ListenRules.SWITCH_FRAMES] shown frames there in a row; one frame back on the identified note starts the count
     * again.
     */
    private fun identify(heard: NoteReading): Int {
        val current = identifiedMidi.takeIf { reading != null }
        if (current == null || heard.midi == current) {
            driftFrames = 0
            return heard.midi
        }
        val reach = abs(NoteMath.centsBetween(heard.frequency, current))
        if (reach > ListenRules.NEAR_CENTS || ++driftFrames >= ListenRules.SWITCH_FRAMES) {
            driftFrames = 0
            return heard.midi
        }
        return current
    }

    /** Silence or a frame too uncertain to trust: hold the reading for a while, then clear it. */
    private fun unpitched(decision: FrameDecision): FrameDecision {
        silentFrames++
        if (silentFrames >= ListenRules.HOLD_FRAMES) {
            window.clear()
            reading = null
        }
        return decision
    }
}
