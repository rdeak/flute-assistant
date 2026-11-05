// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import org.junit.Assert.assertEquals
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
        val run = listen(FluteSound.sound(gapsMs = 50, secondDb = -30.0))
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

    /** Frames [samples] as the engine does and runs the flute's listening path over them. */
    private fun listen(samples: DoubleArray): ListenPath.Run = ListenPath.run(FluteSound.render(samples), rate, tune)
}
