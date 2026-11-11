// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Which click to render: the bar's first beat or any other. */
enum class ClickVariant { ACCENT, BEAT }

/**
 * Procedurally synthesises the click samples, so the app needs no bundled audio assets and
 * every sound is generated at the device's native sample rate (no resampling latency).
 *
 * The click is a woodblock: a short, bright "tok" of decaying partials plus a 2 ms noise
 * transient. All output is mono float PCM normalised to a 0.9 peak and faded to exact silence
 * at the end.
 */
object ClickSynth {

    private const val PEAK = 0.9f

    fun render(variant: ClickVariant, sampleRate: Int): FloatArray {
        val (f0, durationMs) = when (variant) {
            ClickVariant.ACCENT -> 2200.0 to 70.0
            ClickVariant.BEAT -> 1600.0 to 60.0
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
        return normalise(fadeOut(out, frames(5.0, sampleRate)))
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
