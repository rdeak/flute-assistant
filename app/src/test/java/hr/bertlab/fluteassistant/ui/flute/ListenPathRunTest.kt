// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.ui.tuner.FrameDecision
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ListenPath] itself: how it frames audio and what it reports per hop; the sound cases are in [ListenPathTest]. */
class ListenPathRunTest {

    private val rate = 48_000
    private val hop = AudioRecordTunerEngine.HOP_SIZE

    @Test
    fun `the run lists one step per hop with the waited index, a partial hop left over`() {
        val run = ListenPath.run(FloatArray(10 * hop + 100), rate, listOf(TuneRest(QUARTER), TuneNote(71, QUARTER)))
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
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 400)), rate, tune)
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
        val heard = ListenPath.run(samples, rate, tune).heard.size
        val deaf = ListenPath.run(samples, rate, tune, rules = NoteOnsetTracker.Rules(stopDrop = 0.5f)).heard.size
        assertTrue("stopDrop 0.5 hears no tongued stop: $deaf of $heard", deaf < heard)
        // One hop of noise louder than the seeded floor: the floor rises by one FLOOR_RISE from the seed, not from nothing.
        val noise = FloatArray(hop) { if (it % 2 == 0) 0.1f else -0.1f }
        assertEquals(0.3f + NoteOnsetTracker.FLOOR_RISE, ListenPath.run(noise, rate, tune, floor = 0.3f).steps.single().floor!!, 1e-6f)
    }
}
