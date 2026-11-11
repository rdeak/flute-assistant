// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SustainedToneSynthTest {

    private val sampleRate = 48_000

    /** Renders [ms] of audio in 256-sample blocks, the way a player would: block boundaries are where bugs hide. */
    private fun SustainedToneSynth.render(ms: Int): FloatArray {
        val out = FloatArray(sampleRate * ms / 1000)
        var offset = 0
        while (offset < out.size) {
            val block = FloatArray(minOf(256, out.size - offset))
            render(block)
            block.copyInto(out, offset)
            offset += block.size
        }
        return out
    }

    private fun risingCrossings(samples: FloatArray, from: Int, until: Int): Int {
        var crossings = 0
        for (i in (from + 1) until until) if (samples[i - 1] < 0f && samples[i] >= 0f) crossings++
        return crossings
    }

    @Test
    fun `silent until a note starts`() {
        val synth = SustainedToneSynth(sampleRate)
        assertTrue(synth.isSilent)
        assertTrue(synth.render(100).all { it == 0f })
    }

    @Test
    fun `fades in over the attack and holds at the amplitude`() {
        val synth = SustainedToneSynth(sampleRate)
        synth.noteOn(440.0, 0.5f)
        val s = synth.render(1000)
        assertTrue(abs(s[0]) < 0.01f)
        val attackSamples = sampleRate * SustainedToneSynth.ATTACK_MS / 1000
        val peakOfAttack = s.take(attackSamples).maxOf { abs(it) }
        val peakOfAll = s.maxOf { abs(it) }
        assertTrue("attack peak $peakOfAttack should be below $peakOfAll", peakOfAttack < peakOfAll)
        val peakOfHold = s.drop(sampleRate / 2).maxOf { abs(it) }
        assertEquals(0.5f, peakOfHold, 0.01f)
        assertFalse(synth.isSilent)
    }

    @Test
    fun `oscillates at the requested frequency`() {
        val synth = SustainedToneSynth(sampleRate)
        synth.noteOn(440.0, 0.5f)
        val s = synth.render(1000)
        assertEquals(220.0, risingCrossings(s, sampleRate / 2, sampleRate).toDouble(), 2.0)
    }

    @Test
    fun `fades out after note off and reports silence`() {
        val synth = SustainedToneSynth(sampleRate)
        synth.noteOn(440.0, 0.5f)
        synth.render(500)
        synth.noteOff()
        val t = synth.render(200)
        assertTrue(abs(t.last()) < 1e-3f)
        assertTrue(synth.isSilent)
        assertTrue(synth.render(50).all { it == 0f })
    }

    @Test
    fun `a new note changes the frequency`() {
        val synth = SustainedToneSynth(sampleRate)
        synth.noteOn(440.0, 0.5f)
        synth.render(300)
        synth.noteOn(660.0, 0.5f)
        val u = synth.render(500)
        assertEquals(165.0, risingCrossings(u, u.size - sampleRate / 4, u.size).toDouble(), 2.0)
    }

    /** Every playback note starts this way: 80 ms into the previous note's 120 ms release. */
    @Test
    fun `no step when a new note starts during the release`() {
        val synth = SustainedToneSynth(sampleRate)
        synth.noteOn(261.63, 0.5f)
        val a = synth.render(300)
        synth.noteOff()
        val b = synth.render(80)
        synth.noteOn(392.0, 0.5f)
        val c = synth.render(300)
        val x = a + b + c
        var largest = 0f
        for (i in 1 until x.size) largest = maxOf(largest, abs(x[i] - x[i - 1]))
        assertTrue("largest step $largest", largest <= 0.06f)
    }

    @Test
    fun `no step at note on, note off or a new note while sounding`() {
        val synth = SustainedToneSynth(sampleRate)
        synth.noteOn(261.63, 0.5f)
        val a = synth.render(300)
        synth.noteOn(392.0, 0.5f)
        val b = synth.render(300)
        synth.noteOff()
        val c = synth.render(300)
        val x = a + b + c
        var largest = 0f
        for (i in 1 until x.size) largest = maxOf(largest, abs(x[i] - x[i - 1]))
        // The waveform's own steepest step at 392 Hz and this amplitude is about 0.043; an envelope jump shows.
        assertTrue("largest step $largest", largest <= 0.06f)
    }
}
