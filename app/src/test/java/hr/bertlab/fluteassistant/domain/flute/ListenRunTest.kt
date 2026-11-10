// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A "Your turn" fed hand-made frames, no microphone and no coroutines; real sound is `ListenPathTest`'s. */
class ListenRunTest {

    private fun q(midi: Int) = TuneNote(midi, QUARTER)
    private fun pitched(midi: Int) = PitchFrame(NoteMath.midiToFrequency(midi), 0.97, level = 0.4f)
    private val silent = PitchFrame(null, 0.0, level = 0f)

    /** Enough agreeing frames for [midi] to lock: the note begins on the last. */
    private fun ListenRun.begin(midi: Int): ListenRun.Step =
        List(ListenRules.LOCK_FRAMES) { onFrame(pitched(midi)) }.last()

    /** The gap that ends the sounding note. */
    private fun ListenRun.stop() = repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { onFrame(silent) }

    @Test
    fun `it waits on the first note, and a right note in any octave earns a star and moves on`() {
        val run = ListenRun(listOf(TuneRest(QUARTER), q(60), q(62)))
        assertEquals(Listening(index = 1), run.listening)
        val step = run.begin(72)
        assertEquals(ListenRun.Heard(index = 1, expected = 60, NoteOnsetTracker.Onset(72, fresh = true), Verdict.RIGHT), step.heard)
        assertTrue(step.advanced)
        assertFalse(step.done)
        assertEquals(mapOf(1 to StepMark.FIRST_TRY), run.marks)
        assertEquals(2, run.listening.index)
        assertEquals("the frame is kept as it was judged", 1, step.row.waited)
        assertEquals(60, step.row.expected)
        assertEquals(Verdict.RIGHT, step.row.verdict)
    }

    @Test
    fun `a wrong note stays shown until the right one, which earns a faint star`() {
        val run = ListenRun(listOf(q(60), q(62)))
        val wrong = run.begin(64)
        assertFalse(wrong.advanced)
        assertEquals(WrongNote(index = 0, midi = 64), run.listening.wrong)
        run.stop()
        assertEquals("no timer takes it away", WrongNote(0, 64), run.listening.wrong)
        run.begin(60)
        assertNull(run.listening.wrong)
        assertEquals(mapOf(0 to StepMark.RETRY), run.marks)
    }

    @Test
    fun `the note sounding shows under the step waited on, but not the tail of the note just counted`() {
        val run = ListenRun(listOf(q(60), q(62)))
        run.begin(64)
        assertEquals(64, run.listening.heardNow)
        run.stop()
        assertNull(run.listening.heardNow)
        run.begin(60) // counted; it goes on while d1 is waited on
        run.onFrame(pitched(60))
        assertNull(run.listening.heardNow)
    }

    @Test
    fun `a written repeat held on instead of started again asks to stop and play it again`() {
        val run = ListenRun(listOf(q(60), q(60)))
        run.begin(60)
        assertEquals("the repeat waited on is just what the child must see", 60, run.listening.heardNow)
        repeat(ListenRun.REPEAT_ADVICE_FRAMES - 1) { run.onFrame(pitched(60)) }
        assertEquals(TunerAdvice.NONE, run.listening.advice)
        run.onFrame(pitched(60))
        assertTrue(run.listening.repeatHeld)
        assertEquals(TunerAdvice.PLAY_AGAIN, run.listening.advice)
        run.stop()
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `the last note counted, the run goes on until its sound ends`() {
        val run = ListenRun(listOf(q(60)))
        val step = run.begin(60)
        assertTrue(step.done)
        assertTrue(run.listening.finishing)
        assertEquals("the animal stays over the last note", 0, run.listening.index)
        assertFalse(run.isOver)
        run.stop()
        assertTrue(run.isOver)
    }

    @Test
    fun `a last note held on ends the run after FINISH_FRAMES`() {
        val run = ListenRun(listOf(q(60)))
        run.begin(60)
        repeat(ListenRun.FINISH_FRAMES - 1) { run.onFrame(pitched(60)) }
        assertFalse(run.isOver)
        run.onFrame(pitched(60))
        assertTrue(run.isOver)
    }

    @Test
    fun `sound that never becomes a note changes the hearing, reported once with its mean level`() {
        val run = ListenRun(listOf(q(60)))
        val noise = PitchFrame(null, 0.0, level = 0.3f)
        val steps = List(HearingTracker.UNCLEAR_FRAMES) { run.onFrame(noise) }
        assertTrue(steps.dropLast(1).all { it.hearingMean == null })
        assertEquals(0.3, steps.last().hearingMean!!, 1e-6)
        assertEquals(Hearing.QUIET_UNCLEAR, run.listening.hearing)
        assertEquals(TunerAdvice.COME_CLOSER, run.listening.advice)
        assertNull("unchanged: not reported again", run.onFrame(noise).hearingMean)
    }

    @Test
    fun `the room's floor carries from one run to the next`() {
        val first = ListenRun(listOf(q(60)))
        first.onFrame(PitchFrame(null, 0.0, level = 0.2f))
        assertEquals(0.2f, first.floor)
        assertEquals(0.2f, ListenRun(listOf(q(60)), floor = first.floor).floor)
    }
}
