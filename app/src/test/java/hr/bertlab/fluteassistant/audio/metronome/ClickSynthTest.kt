// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.SoundProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSynthTest {

    private val sampleRate = 48_000

    @Test
    fun `every sound is short, normalised and ends in silence`() {
        for (profile in SoundProfile.entries) {
            for (variant in ClickVariant.entries) {
                val pcm = ClickSynth.render(profile, variant, sampleRate)
                val ms = pcm.size * 1000.0 / sampleRate
                assertTrue("$profile/$variant too short ($ms ms)", ms >= 10.0)
                assertTrue("$profile/$variant too long ($ms ms)", ms <= 400.0)

                val peak = pcm.maxOf { abs(it) }
                assertTrue("$profile/$variant peak $peak > 1", peak <= 1.0f)
                assertTrue("$profile/$variant peak $peak too quiet", peak >= 0.5f)

                val tail = pcm.takeLast(maxOf(1, pcm.size / 100))
                assertTrue("$profile/$variant does not fade out", tail.all { abs(it) < 0.05f })
            }
        }
    }

    @Test
    fun `accent differs from the plain beat`() {
        for (profile in SoundProfile.entries) {
            val accent = ClickSynth.render(profile, ClickVariant.ACCENT, sampleRate)
            val beat = ClickSynth.render(profile, ClickVariant.BEAT, sampleRate)
            assertFalse("$profile accent == beat", accent.contentEquals(beat))
        }
    }

    @Test
    fun `rendering is deterministic`() {
        val a = ClickSynth.render(SoundProfile.WOODBLOCK, ClickVariant.BEAT, sampleRate)
        val b = ClickSynth.render(SoundProfile.WOODBLOCK, ClickVariant.BEAT, sampleRate)
        assertArrayEquals(a, b, 0f)
    }
}
