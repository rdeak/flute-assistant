// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** [ListenPath] itself: how it frames audio and what it reports per hop; the sound cases are in [ListenPathTest]. */
class ListenPathRunTest {

    private val rate = 48_000
    private val hop = AudioRecordTunerEngine.HOP_SIZE
    /** These cases are about the path, not the length rule: they run at 60 a minute, most of them pitch only. */
    private val pulse = Pulse(60)

    @Test
    fun `the run lists one step per hop with the waited index, a partial hop left over`() {
        val run = ListenPath.run(FloatArray(10 * hop + 100), rate, listOf(TuneRest(QUARTER), TuneNote(71, QUARTER)), pulse)
        assertEquals(10, run.steps.size)
        assertEquals((1..10).toList(), run.steps.map { it.frame })
        assertTrue(run.steps.all { it.decision == FrameDecision.SILENT && it.sounding == null && it.onset == null && it.verdict == null })
        assertTrue("the rest is skipped", run.steps.all { it.waited == 1 && it.expected == 71 })
        assertTrue(run.heard.isEmpty())
        assertTrue(run.marks.isEmpty())
    }

    @Test
    fun `a note is judged while one is waited on and only reported after the tune is done`() {
        val tune = listOf(TuneNote(71, QUARTER))
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 400)), rate, tune, pulse, run = ListenRun.Rules.PITCH_ONLY)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
        val judged = run.steps.filter { it.verdict != null }
        assertEquals(1, judged.size)
        assertEquals(Verdict.RIGHT, judged.single().verdict)
        assertEquals(0, judged.single().waited)
        val later = run.steps.filter { it.onset != null && it.verdict == null }
        assertEquals("the second and third notes begin after the tune is done: heard, not judged", 2, later.size)
        assertTrue(later.all { it.waited == null && it.expected == null })
        assertEquals(3, run.heard.size)
        assertNull(run.heard[1].index)
    }

    @Test
    fun `a seeded floor and other rules reach the tracker`() {
        val samples = FluteSound.render(FluteSound.sound(gapsMs = 50))
        val tune = List(3) { TuneNote(71, QUARTER) }
        val heard = ListenPath.run(samples, rate, tune, pulse, run = ListenRun.Rules.PITCH_ONLY).heard.size
        val deaf = ListenPath.run(samples, rate, tune, pulse, rules = NoteOnsetTracker.Rules(stopDrop = 0.5f), run = ListenRun.Rules.PITCH_ONLY).heard.size
        assertTrue("stopDrop 0.5 hears no tongued stop: $deaf of $heard", deaf < heard)
        // One hop of noise louder than the seeded floor: the floor rises by one FLOOR_RISE from the seed, not from nothing.
        // Random, not a +/- pattern: that is a tone at half the rate, and the detector reads the newest hop.
        val random = Random(3)
        val noise = FloatArray(hop) { (random.nextFloat() - 0.5f) * 0.2f }
        val step = ListenPath.run(noise, rate, tune, pulse, floor = 0.3f).steps.single()
        assertEquals(step.toString(), 0.3f + NoteOnsetTracker.FLOOR_RISE, step.floor!!, 1e-6f)
    }

    @Test
    fun `frames detected once give the same run under any rules`() {
        // The detector is no part of the rules: a sweep detects a recording once and replays the frames.
        val samples = FluteSound.render(FluteSound.sound(gapsMs = 50))
        val tune = List(3) { TuneNote(71, QUARTER) }
        val frames = ListenPath.frames(samples, rate)
        assertEquals(samples.size / hop, frames.frames.size)
        assertEquals(rate, frames.rate)
        assertTrue(frames.frames.all { it.hopMs == PitchFrame.hopMsAt(rate) })
        for (rules in listOf(ListenRun.Rules(), ListenRun.Rules.PITCH_ONLY)) {
            val once = ListenPath.run(frames, tune, Pulse(120), run = rules)
            val again = ListenPath.run(samples, rate, tune, Pulse(120), run = rules)
            assertEquals(again.steps, once.steps)
            assertEquals(again.marks, once.marks)
            assertEquals(again.heard, once.heard)
        }
    }

    @Test
    fun `a step says where the animal is`() {
        // At 60 a quarter needs 250 ms, so the note is walked for a few frames after its onset before it is earned.
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 400)), rate, listOf(TuneNote(71, QUARTER), TuneNote(71, QUARTER)), Pulse(60))
        val walks = run.steps.mapNotNull { it.walk }
        assertTrue(walks.any { it.index == 0 && it.phase == Walk.Phase.SOUNDING })
        assertTrue(walks.any { it.index == 0 && it.phase == Walk.Phase.DONE })
        assertTrue(walks.any { it.index == 1 })
    }
}
