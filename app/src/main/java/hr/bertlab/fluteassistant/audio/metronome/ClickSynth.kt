// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.SoundProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Which flavour of a sound profile to render. */
enum class ClickVariant { ACCENT, BEAT, SUBDIVISION }

/**
 * Procedurally synthesises the click samples, so the app needs no bundled audio assets and
 * every sound is generated at the device's native sample rate (no resampling latency).
 *
 * All output is mono float PCM normalised to a 0.9 peak and faded to exact silence at the end.
 */
object ClickSynth {

    private const val PEAK = 0.9f

    fun render(profile: SoundProfile, variant: ClickVariant, sampleRate: Int): FloatArray {
        val pcm = when (profile) {
            SoundProfile.WOODBLOCK -> woodblock(variant, sampleRate)
            SoundProfile.BEEP -> beep(variant, sampleRate)
            SoundProfile.BELL -> bell(variant, sampleRate)
        }
        return normalise(pcm)
    }

    // -- Woodblock: a short, bright "tok" - decaying partials plus a 2 ms noise transient. --
    private fun woodblock(variant: ClickVariant, sampleRate: Int): FloatArray {
        val (f0, durationMs) = when (variant) {
            ClickVariant.ACCENT -> 2200.0 to 70.0
            ClickVariant.BEAT -> 1600.0 to 60.0
            ClickVariant.SUBDIVISION -> 1300.0 to 40.0
        }
        val n = frames(durationMs, sampleRate)
        val noise = Lcg(seed = 0x5EED + variant.ordinal)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val body = sin(2 * PI * f0 * t) * exp(-t / 0.008)
            val overtone = 0.4 * sin(2 * PI * f0 * 2.4 * t) * exp(-t / 0.004)
            val transient = 0.6 * noise.next() * exp(-t / 0.001)
            out[i] = (body + overtone + transient).toFloat()
        }
        return fadeOut(out, frames(5.0, sampleRate))
    }

    // -- Digital beep: a pure sine with a soft attack/release envelope. --
    private fun beep(variant: ClickVariant, sampleRate: Int): FloatArray {
        val (f0, durationMs) = when (variant) {
            ClickVariant.ACCENT -> 1568.0 to 45.0   // G6
            ClickVariant.BEAT -> 1046.5 to 45.0     // C6
            ClickVariant.SUBDIVISION -> 784.0 to 30.0 // G5
        }
        val n = frames(durationMs, sampleRate)
        val attack = frames(2.0, sampleRate)
        val release = frames(8.0, sampleRate)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = when {
                i < attack -> i.toDouble() / attack
                i >= n - release -> (n - 1 - i).toDouble() / release
                else -> 1.0
            }
            out[i] = (env * sin(2 * PI * f0 * t)).toFloat()
        }
        return out
    }

    // -- Bell: inharmonic partials with long decays, like a small desk bell. --
    private fun bell(variant: ClickVariant, sampleRate: Int): FloatArray {
        val (f0, durationMs, decayScale) = when (variant) {
            ClickVariant.ACCENT -> Triple(1760.0, 300.0, 1.0)   // A6
            ClickVariant.BEAT -> Triple(1318.5, 180.0, 0.6)     // E6
            ClickVariant.SUBDIVISION -> Triple(1046.5, 100.0, 0.35)
        }
        val ratios = doubleArrayOf(1.0, 2.0, 2.76, 4.07)
        val amplitudes = doubleArrayOf(1.0, 0.55, 0.35, 0.2)
        val decays = doubleArrayOf(0.25, 0.15, 0.10, 0.06).map { it * decayScale }
        val n = frames(durationMs, sampleRate)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (p in ratios.indices) {
                v += amplitudes[p] * sin(2 * PI * f0 * ratios[p] * t) * exp(-t / decays[p])
            }
            out[i] = v.toFloat()
        }
        return fadeOut(out, n / 5)
    }

    private fun frames(ms: Double, sampleRate: Int): Int = (ms / 1000.0 * sampleRate).toInt()

    /** Raised-cosine fade over the last [fadeFrames] samples, guaranteeing a silent tail. */
    private fun fadeOut(pcm: FloatArray, fadeFrames: Int): FloatArray {
        val fade = min(fadeFrames, pcm.size)
        val start = pcm.size - fade
        for (i in 0 until fade) {
            val x = (i + 1).toDouble() / fade
            val gain = 0.5 * (1 + kotlin.math.cos(PI * x))
            pcm[start + i] = (pcm[start + i] * gain).toFloat()
        }
        if (pcm.isNotEmpty()) pcm[pcm.size - 1] = 0f
        return pcm
    }

    private fun normalise(pcm: FloatArray): FloatArray {
        var peak = 0f
        for (v in pcm) peak = maxOf(peak, abs(v))
        if (peak > 0f) {
            val gain = PEAK / peak
            for (i in pcm.indices) pcm[i] *= gain
        }
        return pcm
    }

    /** Tiny deterministic noise source so renders are reproducible. */
    private class Lcg(seed: Int) {
        private var state = seed.toLong()
        fun next(): Double {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            return ((state ushr 11).toDouble() / (1L shl 53)) * 2.0 - 1.0
        }
    }
}
