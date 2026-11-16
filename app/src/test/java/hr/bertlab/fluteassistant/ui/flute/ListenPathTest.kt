// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.ListenLevel
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Your turn" end to end on synthetic sound: a flute-like tone in a room, framed as
 * `AudioRecordTunerEngine` frames the microphone (48 kHz, a 4096 window advanced by 2048), run
 * through the real [YinPitchDetector], [NoteOnsetTracker] and [TuneCheck]. These are the cases
 * a phone test with a real flute found or would find (2026-11-04: a child's "h1 h1 long h1" lost
 * its tongued repeat, so the long note was counted as the second and the run waited for a third);
 * the emulator has no microphone, so this is where the whole path is checked.
 */
class ListenPathTest {

    private val rate = FluteSound.RATE

    /** The tune written: three b1 / B4, the last one long. */
    private val tune = listOf(TuneNote(71, NoteLength.QUARTER), TuneNote(71, NoteLength.QUARTER), TuneNote(71, NoteLength.HALF))

    @Test
    fun `three repeated notes with tongued gaps of 50 ms are all counted`() {
        // "ta ta taaa": the tongue stops the air for about 50 ms between notes of the same pitch.
        val run = listen(FluteSound.sound(gapsMs = 50, roomDb = -10.0, floorDb = -50.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `the quickest tongue, 40 ms, is still heard in a lively room`() {
        val run = listen(FluteSound.sound(gapsMs = 40, roomDb = -6.0, rt60 = 0.6, floorDb = -42.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `a repeat played softer than the note before it is still counted`() {
        // Eight decibels softer, as it was at the synthetic default (-22 and -30), now at a real flute's level at the
        // phone: at -30 the repeat lay on the tracker's floor (0.45, about -33 dBFS of RMS), which is the room's side.
        val run = listen(FluteSound.sound(gapsMs = 50, toneDb = -14.0, secondDb = -22.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `a tone wobbling two decibels is not heard as notes started again`() {
        val run = listen(FluteSound.sound(gapsMs = 400, wobbleDb = 2.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    @Test
    fun `a long note fading away as the breath runs out is one note`() {
        val run = listen(FluteSound.sound(gapsMs = 400, fadeDb = -14.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    @Test
    fun `three repeated notes with breath-sized pauses are all counted in a lively room`() {
        val run = listen(FluteSound.sound(gapsMs = 400, roomDb = -6.0, rt60 = 0.6, floorDb = -46.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `notes not far over the room's floor are all counted after a short pause back at the floor`() {
        // A note at -22 dBFS over a room at -38: half the note's level (-41 dBFS) lies under the
        // floor, so a pause of 400 ms (too short for the hold count) is a gap only by the floor.
        val run = listen(FluteSound.sound(gapsMs = 400, toneDb = -22.0, floorDb = -38.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `notes parted by long noisy breaths are all counted once the hold count runs out`() {
        // The child inhales loudly between notes: the pauses stay over half the note's level and
        // over the floor the quiet lead-in taught, so only their length ends the note.
        val run = listen(FluteSound.sound(gapsMs = 1500, toneDb = -22.0, floorDb = -46.0, pauseDb = -38.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `one long note sagging flat as the breath runs out is one note`() {
        val run = listen(FluteSound.sound(gapsMs = 400, sagCents = -60.0, floorDb = -50.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    @Test
    fun `a held note the gate blinks on is one note`() {
        // Breath noise over the tone for two frames: the pitch is lost but the loudness stays.
        val run = listen(FluteSound.sound(gapsMs = 400, blinkDb = -24.0, floorDb = -50.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    /**
     * Before the child plays the room is not silent: a voice across it or a television has a clear pitch too, far
     * under a flute at the phone. On the user's phone (2026-11-10) twenty seconds of that earned a star and five
     * wrong marks before the first note. Here the three notes sound twice, 26 dB apart: the faint ones are the
     * room's and nothing is judged while they sound, the loud ones are the child's and earn the three stars. With
     * the floor switched off the phone of that day is back: the room's notes are the ones judged.
     */
    @Test
    fun `sound far too faint to be the flute at the phone is not judged before the child plays`() {
        val room = FluteSound.sound(gapsMs = 400, toneDb = -40.0, floorDb = -60.0)
        val child = FluteSound.sound(gapsMs = 400, toneDb = -14.0, floorDb = -60.0)
        val roomMs = room.size * 1000L / rate

        val run = listen(room + child)
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(run.heard.toString(), 3, run.heard.size)
        assertTrue("nothing is judged while only the room sounds: ${run.heard}", run.heard.all { it.atMs > roomMs })

        val before = listen(room + child, NoteOnsetTracker.Rules(minLevel = 0f))
        assertTrue("without the floor the room's sound is judged: ${before.heard}", before.heard.any { it.index != null && it.atMs < roomMs })
    }

    // ---- The length rule on sound (2026-11-11): the notes are 400, 400 and 1500 ms ----

    @Test
    fun `a rest in the tune is walked on sound, and the note after it counts`() {
        // At 120 a minute the quarter rest lasts 500 ms; the 400 ms gap ends inside it, and that note counts at once.
        val withRest = listOf(TuneNote(71, NoteLength.QUARTER), TuneRest(NoteLength.QUARTER), TuneNote(71, NoteLength.QUARTER), TuneNote(71, NoteLength.HALF))
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 400)), rate, withRest, Pulse(120))
        assertTrue("the rest was walked", run.steps.any { it.walk?.phase == Walk.Phase.REST })
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY, 3 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `notes long enough at their tempo earn their stars`() {
        // At 120 a minute a quarter needs 250 ms and the half note 750.
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 50)), rate, tune, Pulse(120))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `a note too short for a slow tempo gets the hourglass`() {
        // At 40 a minute a quarter needs 375 ms and the half note 1875: the quarters are long enough, and the 1500 ms
        // note is let go too early for the half note when its sound ends.
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 50)), rate, tune, Pulse(40))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.SHORT), run.marks)
    }

    @Test
    fun `under the rule before 2026-11-14 that note is cut short and waited on`() {
        val waits = ListenRun.Rules(shortMovesOn = false)
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 50)), rate, tune, Pulse(40), run = waits)
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY), run.marks)
        assertEquals(Walk.Phase.SHORT, run.steps.last().walk?.phase)
        assertEquals(2, run.steps.last().walk?.index)
    }

    @Test
    fun `three b's blown 60 cents high are walked and each earns the down arrow`() {
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 50, cents = 60.0)), rate, tune, Pulse(120))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.HIGH, 1 to StepMark.HIGH, 2 to StepMark.HIGH), run.marks)
    }

    @Test
    fun `a note 60 cents under the written one is a wrong note`() {
        val cs = listOf(TuneNote(72, NoteLength.QUARTER), TuneNote(72, NoteLength.QUARTER), TuneNote(72, NoteLength.HALF))
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 50, cents = 40.0)), rate, cs, Pulse(120))
        assertTrue(run.heard.toString(), run.marks.isEmpty())
        assertTrue(run.steps.any { it.row.verdict == Verdict.WRONG })
    }

    /** Frames [samples] as the engine does and runs the flute's listening path over them, under [rules], pitch only. */
    private fun listen(samples: DoubleArray, rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules()): ListenPath.Run =
        ListenPath.run(FluteSound.render(samples), rate, tune, Pulse(60), rules, run = ListenRun.Rules.PITCH_ONLY)

    // ---- Practice and Exam on sound (2026-11-16): the same notes, 400, 400 and 1500 ms ----

    @Test
    fun `a note held two beats over its length earns the hand`() {
        // At 120 a minute a quarter lasts 500 ms; Practice's room past it is 375 ms, and the third note sounds 1500.
        val quarters = List(4) { TuneNote(71, NoteLength.QUARTER) }
        val run = ListenPath.run(FluteSound.render(FluteSound.sound(gapsMs = 50)), rate, quarters, Pulse(120))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.LONG), run.marks)
    }

    @Test
    fun `a note that drifts thirty cents after its mark earns the arrow in exam and keeps its star in practice`() {
        // The last note rises from the B to 60 cents over it: in tune when it is long enough (250 ms in), 30 high on the whole.
        val quarters = List(3) { TuneNote(71, NoteLength.QUARTER) }
        val sound = FluteSound.render(FluteSound.sound(gapsMs = 50, sagCents = 60.0))
        val exam = ListenPath.run(sound, rate, quarters, Pulse(120), run = ListenLevel.EXAM.rules)
        assertEquals(exam.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.HIGH), exam.marks)
        val practice = ListenPath.run(sound, rate, quarters, Pulse(120), run = ListenLevel.PRACTICE.rules)
        assertEquals(practice.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), practice.marks)
    }
}
