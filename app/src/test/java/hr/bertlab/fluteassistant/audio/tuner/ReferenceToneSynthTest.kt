// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReferenceToneSynthTest {

    private val sampleRate = 48_000

    @Test
    fun `length follows the duration`() {
        assertEquals(96_000, ReferenceToneSynth.render(440.0, sampleRate, durationMs = 2000, amplitude = 0.5f).size)
    }

    @Test
    fun `starts and ends in silence and peaks at the amplitude`() {
        val samples = ReferenceToneSynth.render(440.0, sampleRate, durationMs = 1000, amplitude = 0.5f)
        assertEquals(0f, samples.first(), 1e-6f)
        assertEquals(0f, samples.last(), 1e-3f)
        val peak = samples.maxOf { abs(it) }
        assertEquals(0.5f, peak, 0.01f)
    }

    @Test
    fun `oscillates at the requested frequency`() {
        val samples = ReferenceToneSynth.render(440.0, sampleRate, durationMs = 1000, amplitude = 0.5f)
        // Count rising zero crossings over the middle 0.5 s; the waveform has exactly one per cycle.
        var crossings = 0
        for (i in 12_000 until 36_000) if (samples[i - 1] < 0f && samples[i] >= 0f) crossings++
        assertEquals(220.0, crossings.toDouble(), 2.0)
    }

    @Test
    fun `silent when the amplitude is zero`() {
        assertTrue(ReferenceToneSynth.render(440.0, sampleRate, 200, amplitude = 0f).all { it == 0f })
    }
}
