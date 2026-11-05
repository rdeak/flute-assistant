// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class HearingTrackerTest {

    private val tracker = HearingTracker()

    private fun sound(level: Float, n: Int, decision: FrameDecision = FrameDecision.SILENT) =
        repeat(n) { tracker.onFrame(level, decision) }

    @Test
    fun `quiet sound without a note is noticed after about a second`() {
        sound(0.3f, HearingTracker.UNCLEAR_FRAMES - 1)
        assertEquals(Hearing.CLEAR, tracker.hearing)
        sound(0.3f, 1)
        assertEquals(Hearing.QUIET_UNCLEAR, tracker.hearing)
        assertEquals(0.3, tracker.levelMean, 1e-6)
    }

    @Test
    fun `loud unclear sound is told apart from quiet`() {
        sound(0.6f, HearingTracker.UNCLEAR_FRAMES, FrameDecision.LOW_PROBABILITY)
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
    }

    @Test
    fun `a breath inside the run is tolerated, a longer gap ends it`() {
        sound(0.3f, 20)
        sound(0f, HearingTracker.UNCLEAR_GAP_FRAMES - 1)
        sound(0.3f, 5)
        assertEquals(Hearing.QUIET_UNCLEAR, tracker.hearing)
        sound(0f, HearingTracker.ADVICE_HOLD_FRAMES)
        assertEquals(Hearing.CLEAR, tracker.hearing)
    }

    @Test
    fun `before the advice shows, a short gap still starts the count again`() {
        sound(0.3f, 20)
        sound(0f, HearingTracker.UNCLEAR_GAP_FRAMES)
        sound(0.3f, HearingTracker.UNCLEAR_FRAMES - 1)
        assertEquals(Hearing.CLEAR, tracker.hearing)
    }

    @Test
    fun `once shown, the advice stays through a breath and goes on at once when the sound comes back`() {
        sound(0.6f, HearingTracker.UNCLEAR_FRAMES)
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
        sound(0f, HearingTracker.ADVICE_HOLD_FRAMES - 1) // a child breathing between two unclear notes
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.onFrame(0.6f, FrameDecision.SILENT)) // no second of waiting again
        sound(0f, HearingTracker.ADVICE_HOLD_FRAMES)
        assertEquals(Hearing.CLEAR, tracker.hearing)
    }

    @Test
    fun `a level hovering at the close level keeps the advice it has`() {
        sound(0.48f, HearingTracker.UNCLEAR_FRAMES)
        assertEquals(Hearing.QUIET_UNCLEAR, tracker.hearing)
        sound(0.53f, HearingTracker.UNCLEAR_FRAMES) // past the close level, not past its margin
        assertEquals(Hearing.QUIET_UNCLEAR, tracker.hearing)
        sound(0.58f, HearingTracker.UNCLEAR_FRAMES) // clearly closer
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
        sound(0.47f, HearingTracker.UNCLEAR_FRAMES) // under the close level, not under its margin
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
        sound(0.42f, HearingTracker.UNCLEAR_FRAMES) // clearly further away
        assertEquals(Hearing.QUIET_UNCLEAR, tracker.hearing)
    }

    @Test
    fun `the first verdict still splits at the close level itself`() {
        sound(0.52f, HearingTracker.UNCLEAR_FRAMES)
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
        tracker.reset()
        sound(0.48f, HearingTracker.UNCLEAR_FRAMES)
        assertEquals(Hearing.QUIET_UNCLEAR, tracker.hearing)
    }

    @Test
    fun `a shown note clears the advice at once, even during the hold`() {
        sound(0.6f, HearingTracker.UNCLEAR_FRAMES)
        sound(0f, HearingTracker.UNCLEAR_GAP_FRAMES)
        assertEquals(Hearing.CLEAR, tracker.onFrame(0.6f, FrameDecision.SHOWN))
        assertEquals(0.0, tracker.levelMean, 0.0)
    }

    @Test
    fun `the level is judged on the last second, so moving closer is noticed`() {
        sound(0.3f, HearingTracker.UNCLEAR_FRAMES)
        sound(0.7f, HearingTracker.UNCLEAR_FRAMES)
        assertEquals(Hearing.LOUD_UNCLEAR, tracker.hearing)
        assertEquals(0.7, tracker.levelMean, 1e-6)
    }

    @Test
    fun `a shown note or a reset ends the run at once`() {
        sound(0.3f, HearingTracker.UNCLEAR_FRAMES)
        assertEquals(Hearing.CLEAR, tracker.onFrame(0.4f, FrameDecision.SHOWN))
        sound(0.3f, HearingTracker.UNCLEAR_FRAMES)
        tracker.reset()
        assertEquals(Hearing.CLEAR, tracker.hearing)
        assertEquals(0.0, tracker.levelMean, 0.0)
    }

    @Test
    fun `ignored frames and sound under the floor never start a run`() {
        sound(0.6f, HearingTracker.UNCLEAR_FRAMES, FrameDecision.IGNORED)
        sound(0.1f, 100)
        assertEquals(Hearing.CLEAR, tracker.hearing)
    }
}
