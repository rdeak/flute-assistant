// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetronomeRendererTest {

    private val sampleRate = 48_000
    private val block = 480 // divides one second of audio evenly

    /** Renders [totalFrames] in fixed blocks, returning all events and the concatenated audio. */
    private fun renderSeconds(config: MetronomeConfig, totalFrames: Int): Pair<List<ClickEvent>, FloatArray> {
        val renderer = MetronomeRenderer(sampleRate)
        val audio = FloatArray(totalFrames)
        val events = mutableListOf<ClickEvent>()
        val buffer = FloatArray(block)
        var pos = 0
        while (pos < totalFrames) {
            events += renderer.render(buffer, config)
            buffer.copyInto(audio, pos, 0, minOf(block, totalFrames - pos))
            pos += block
        }
        return events to audio
    }

    @Test
    fun `one second at 120 bpm contains two clicks`() {
        val (events, audio) = renderSeconds(MetronomeConfig(bpm = 120, subdivision = Subdivision.NONE), sampleRate)
        assertEquals(listOf(0L, 24_000L), events.map { it.frame })
        assertEquals(listOf(BeatStrength.STRONG, BeatStrength.WEAK), events.map { it.strength })

        fun energy(from: Int, to: Int) = (from until to).sumOf { abs(audio[it]).toDouble() }
        assertTrue("accent should be audible", energy(0, 2_000) > 1.0)
        assertTrue("second beat should be audible", energy(24_000, 26_000) > 1.0)
        assertTrue("gap before second beat should be silent", energy(20_000, 23_900) == 0.0)
    }

    @Test
    fun `fast overlapping clicks are limited smoothly instead of clipped`() {
        val config = MetronomeConfig(bpm = 300, subdivision = Subdivision.SIXTEENTH, sound = SoundProfile.BELL)
        val (events, audio) = renderSeconds(config, sampleRate)
        assertTrue(events.size >= 19) // 300 bpm * 4 = 20 clicks per second
        assertTrue("stacked bells should exceed the knee", audio.any { abs(it) > SoftLimiter.KNEE })
        assertTrue("limiter must never reach full scale", audio.none { abs(it) >= 1f })
    }

    @Test
    fun `soft limiter is transparent below the knee and monotonic above it`() {
        assertEquals(0.5f, SoftLimiter.limit(0.5f), 0f)
        assertEquals(-0.9f, SoftLimiter.limit(-0.9f), 0f)
        var previous = SoftLimiter.limit(0.9f)
        var x = 0.91f
        while (x < 4f) {
            val y = SoftLimiter.limit(x)
            assertTrue("not monotonic at $x", y >= previous)
            assertTrue("exceeds full scale at $x", y < 1f)
            previous = y
            x += 0.05f
        }
        assertEquals(-SoftLimiter.limit(2f), SoftLimiter.limit(-2f), 0f)
    }

    @Test
    fun `a tempo change moves the already scheduled click`() {
        val renderer = MetronomeRenderer(sampleRate)
        val buffer = FloatArray(block)
        val slow = MetronomeConfig(bpm = 60)   // beat every 48000 frames
        val fast = MetronomeConfig(bpm = 120)  // beat every 24000 frames
        val events = mutableListOf<ClickEvent>()
        events += renderer.render(buffer, slow)                 // click at 0; next pending at 48000
        repeat(sampleRate / block) { events += renderer.render(buffer, fast) }
        assertEquals(listOf(0L, 24_000L, 48_000L), events.take(3).map { it.frame })
    }

    @Test
    fun `volume scales the output`() {
        val renderer = MetronomeRenderer(sampleRate)
        val loud = FloatArray(4_096)
        renderer.render(loud, MetronomeConfig())
        renderer.reset()
        renderer.volume = 0.5f
        val quiet = FloatArray(4_096)
        renderer.render(quiet, MetronomeConfig())
        val ratio = quiet.maxOf { abs(it) } / loud.maxOf { abs(it) }
        assertEquals(0.5f, ratio, 0.01f)
    }

    @Test
    fun `reset starts the pattern again from the accent`() {
        val renderer = MetronomeRenderer(sampleRate)
        val buffer = FloatArray(block)
        val config = MetronomeConfig(bpm = 240)
        repeat(50) { renderer.render(buffer, config) } // ~0.53 s -> a few beats in
        renderer.reset()
        val first = renderer.render(buffer, config).first()
        assertEquals(0L, first.frame)
        assertEquals(BeatStrength.STRONG, first.strength)
        assertEquals(0, first.beatIndex)
    }
}
