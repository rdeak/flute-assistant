// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import kotlin.math.abs

/**
 * Turns a [MetronomeConfig] into a continuous stream of PCM blocks.
 *
 * Owns the [ClickScheduler] (timeline), a cache of synthesised click samples, and the set of
 * clicks currently sounding ("voices"), which it mixes into each block. Clicks that overlap
 * block boundaries carry across calls. The output is scaled by [volume] and passed through
 * [SoftLimiter] so overlapping clicks can never hard-clip.
 *
 * Pure Kotlin: the Android-specific [AudioTrackMetronomePlayer] only moves blocks into an
 * AudioTrack and turns the returned [ClickEvent]s into UI events.
 */
class MetronomeRenderer(val sampleRate: Int) {

    private class Voice(val samples: FloatArray, val gain: Float, val startFrame: Long) {
        var cursor = 0
        val finished: Boolean get() = cursor >= samples.size
    }

    private val scheduler = ClickScheduler(sampleRate)
    private val sampleCache = HashMap<Pair<SoundProfile, ClickVariant>, FloatArray>()
    private val voices = ArrayList<Voice>(8)
    private var framePosition = 0L
    private var lastConfig: MetronomeConfig? = null

    /** Master gain in [0, 1]. Safe to set from any thread. */
    @Volatile
    var volume: Float = 1f

    /** Synthesises every sound/variant combination up front (call off the hot path). */
    fun preload() {
        for (sound in SoundProfile.entries) {
            for (variant in ClickVariant.entries) {
                sampleCache.getOrPut(sound to variant) { ClickSynth.render(sound, variant, sampleRate) }
            }
        }
    }

    /** Rewinds to the start of bar 1 and silences anything still sounding. */
    fun reset() {
        scheduler.reset()
        framePosition = 0L
        lastConfig = null
        voices.clear()
    }

    /**
     * Fills all of [out] with the next block of audio.
     * @return the clicks that start inside this block, in order.
     */
    fun render(out: FloatArray, config: MetronomeConfig): List<ClickEvent> {
        out.fill(0f)
        val blockStart = framePosition
        val blockEnd = blockStart + out.size
        val started = ArrayList<ClickEvent>(2)

        // A pure tempo change moves the upcoming click instead of waiting the old interval out.
        val previous = lastConfig
        if (previous != null && config.bpm != previous.bpm &&
            config.subdivision == previous.subdivision && config.timeSignature == previous.timeSignature
        ) {
            scheduler.retime(config, notBefore = blockStart)
        }
        lastConfig = config

        // Queue every click that begins inside this block. Clicks are only materialised when
        // their block is rendered, so meter/sound changes apply to the very next click too.
        while (scheduler.nextFrame < blockEnd) {
            val click = scheduler.next(config)
            if (click.frame >= blockStart) {
                voices += Voice(samplesFor(config.sound, click.strength), gainFor(click.strength), click.frame)
                started += click
            }
        }

        // Mix the active voices.
        val iterator = voices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            var outIndex = if (voice.cursor == 0) (voice.startFrame - blockStart).toInt() else 0
            while (outIndex < out.size && !voice.finished) {
                out[outIndex++] += voice.samples[voice.cursor++] * voice.gain
            }
            if (voice.finished) iterator.remove()
        }

        val gain = volume
        for (i in out.indices) out[i] = SoftLimiter.limit(out[i] * gain)

        framePosition = blockEnd
        return started
    }

    private fun samplesFor(sound: SoundProfile, strength: BeatStrength): FloatArray {
        val variant = when (strength) {
            BeatStrength.STRONG -> ClickVariant.ACCENT
            BeatStrength.MEDIUM, BeatStrength.WEAK -> ClickVariant.BEAT
            BeatStrength.SUBDIVISION -> ClickVariant.SUBDIVISION
        }
        return sampleCache.getOrPut(sound to variant) { ClickSynth.render(sound, variant, sampleRate) }
    }

    private fun gainFor(strength: BeatStrength): Float = when (strength) {
        BeatStrength.STRONG -> 1.0f
        BeatStrength.MEDIUM -> 0.85f
        BeatStrength.WEAK -> 0.7f
        BeatStrength.SUBDIVISION -> 0.45f
    }
}

/**
 * Transparent below [KNEE]; above it the excess is compressed so the output approaches but
 * never reaches 1.0. Single clicks (peak 0.9) pass untouched; stacked clicks are tamed
 * smoothly instead of being flat-topped.
 */
object SoftLimiter {
    const val KNEE = 0.9f
    private const val SLOPE = 10f

    fun limit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= KNEE) return sample
        val excess = magnitude - KNEE
        val limited = KNEE + excess / (1f + excess * SLOPE)
        return if (sample < 0f) -limited else limited
    }
}
