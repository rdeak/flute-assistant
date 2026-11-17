// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.PlayStep.Moment
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** [Tune.playSteps]: when each step sounds and where the metronome's clicks fall in it. */
class PlayStepsTest {

    @Test
    fun `a note sounds for its length less the gap, a rest is silent and a breath takes no time`() {
        val tune = listOf(TuneNote(60, QUARTER), TuneBreath, TuneRest(QUARTER), TuneNote(67, EIGHTH))
        val steps = Tune.playSteps(tune, Pulse(120))
        assertEquals(listOf(0, 2, 3), steps.map { it.index })
        assertEquals(listOf(60, null, 67), steps.map { it.midi })
        assertEquals(listOf(500L, 500L, 250L), steps.map { it.durationMs })
        assertEquals(listOf(Moment(420, null)), steps[0].inside)
        assertEquals("a rest has no end to sound", emptyList<Moment>(), steps[1].inside)
        assertEquals("the gap is at most a quarter of the note", listOf(Moment(188, null)), steps[2].inside)
    }

    @Test
    fun `in four four a click falls on every quarter, counted from the start of the tune`() {
        val steps = Tune.playSteps(listOf(TuneNote(60, EIGHTH), TuneNote(62, EIGHTH), TuneNote(64, HALF)), Pulse(120))
        assertEquals("the second eighth starts off the click", listOf(true, false, true), steps.map { it.clickAtStart })
        assertEquals(
            "the half note's second beat, then its end",
            listOf(Moment(500, halfBeat = 2), Moment(920, null)),
            steps[2].inside,
        )
    }

    @Test
    fun `in six eight a click is an eighth note, so a quarter holds two`() {
        val step = Tune.playSteps(listOf(TuneNote(60, QUARTER)), Pulse(120, TimeSignature.SIX_EIGHT)).single()
        assertEquals(1000L, step.durationMs)
        assertEquals(true, step.clickAtStart)
        assertEquals(listOf(Moment(500, halfBeat = 1), Moment(920, null)), step.inside)
    }

    // ---- Joined notes (2026-11-16): no gap before a joined note, and a tie is one sound ----

    @Test
    fun `a note joined to the one before has no gap before it`() {
        val steps = Tune.playSteps(listOf(TuneNote(60, QUARTER), TuneNote(62, QUARTER, joined = true)), Pulse(60))
        assertTrue(steps[0].inside.none { it.halfBeat == null })
        assertFalse(steps[0].joined)
        assertTrue(steps[1].joined)
        assertFalse(steps[1].tied) // another note: it glides
        assertEquals(1000L - Tune.gapMs(1000L), steps[1].inside.single { it.halfBeat == null }.atMs)
    }

    @Test
    fun `a tie is one sound`() {
        val steps = Tune.playSteps(listOf(TuneNote(60, QUARTER), TuneNote(60, QUARTER, joined = true)), Pulse(60))
        assertTrue(steps[0].inside.none { it.halfBeat == null })
        assertTrue(steps[1].joined)
        assertTrue(steps[1].tied) // the same note: nothing starts
    }

    @Test
    fun `a note before a rest or a note not joined keeps its gap`() {
        val steps = Tune.playSteps(listOf(TuneNote(60, QUARTER), TuneNote(62, QUARTER), TuneNote(64, QUARTER, joined = true)), Pulse(60))
        assertEquals(1, steps[0].inside.count { it.halfBeat == null })
        assertTrue(steps[1].inside.none { it.halfBeat == null })
    }
}
