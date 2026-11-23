// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The tuner's reading rules, fed frame by frame; `TunerViewModelTest` covers the wiring over them. */
class ReadingTrackerTest {

    private val tracker = ReadingTracker()

    private fun frame(frequency: Double?, probability: Double = 0.95) =
        PitchFrame(frequency = frequency, probability = probability, level = 0.5f, minLevel = 0.5f, hopMs = PitchFrame.hopMsAt(48_000))

    private fun hear(midi: Int, frames: Int, cents: Double = 0.0): FrameDecision {
        val frequency = NoteMath.midiToFrequency(midi) * Math.pow(2.0, cents / 1200.0)
        var last = FrameDecision.SILENT
        repeat(frames) { last = tracker.onFrame(frame(frequency)) }
        return last
    }

    private fun silence(frames: Int) = repeat(frames) { tracker.onFrame(frame(null)) }

    @Test
    fun `a reading shows once the lock frames agree, not before`() {
        assertEquals(FrameDecision.UNSTEADY, hear(69, ListenRules.LOCK_FRAMES - 1))
        assertNull(tracker.reading)
        assertEquals(FrameDecision.SHOWN, hear(69, 1))
        assertEquals(69, tracker.reading?.midi)
        assertEquals(69, tracker.identifiedMidi)
    }

    @Test
    fun `an uncertain frame is not read`() {
        hear(69, ListenRules.LOCK_FRAMES)
        assertEquals(FrameDecision.LOW_PROBABILITY, tracker.onFrame(frame(880.0, probability = ListenRules.MIN_PROBABILITY - 0.01)))
        assertEquals(69, tracker.reading?.midi)
    }

    @Test
    fun `silence holds the reading for the hold frames, then clears it`() {
        hear(69, ListenRules.LOCK_FRAMES)
        silence(ListenRules.HOLD_FRAMES - 1)
        assertEquals(69, tracker.reading?.midi)
        silence(1)
        assertNull(tracker.reading)
    }

    @Test
    fun `a clear step is taken at once`() {
        hear(69, ListenRules.LOCK_FRAMES)
        hear(71, ListenRules.SMOOTHING_WINDOW) // the median reaches the new note well before the switch frames
        assertEquals(71, tracker.identifiedMidi)
    }

    @Test
    fun `a drift just past halfway keeps the identified note until the switch frames`() {
        hear(69, ListenRules.SMOOTHING_WINDOW)
        // 60 cents sharp: nearer to B♭ (70) than to A, but within reach of A, so the ring stays on A for a while.
        var shown = 0
        while (tracker.reading?.midi != 70) hear(69, 1, cents = 60.0)
        while (tracker.identifiedMidi == 69) {
            hear(69, 1, cents = 60.0)
            shown++
        }
        assertEquals(70, tracker.identifiedMidi)
        assertEquals(ListenRules.SWITCH_FRAMES - 1, shown)
    }

    @Test
    fun `reset clears the reading and keeps the note the ring named`() {
        hear(69, ListenRules.LOCK_FRAMES)
        tracker.reset()
        assertNull(tracker.reading)
        assertEquals(69, tracker.identifiedMidi)
        // Afresh: the frames before the reset do not count towards the next lock.
        assertEquals(FrameDecision.UNSTEADY, hear(69, 1))
    }
}
