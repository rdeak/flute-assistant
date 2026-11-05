// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import hr.bertlab.fluteassistant.audio.flute.FluteTimbre
import kotlin.math.PI
import kotlin.math.min

/**
 * Renders a flute-like reference tone ([FluteTimbre]) with a short fade in and a longer fade
 * out so it neither clicks nor stops dead. Pure Kotlin.
 */
object ReferenceToneSynth {
    private const val ATTACK_MS = 30
    private const val RELEASE_MS = 120

    /** [durationMs] of mono float PCM at [sampleRate], peaking at [amplitude] (0..1). */
    fun render(frequencyHz: Double, sampleRate: Int, durationMs: Int, amplitude: Float): FloatArray {
        require(frequencyHz > 0.0) { "frequency must be positive" }
        require(sampleRate > 0) { "sample rate must be positive" }
        val count = (sampleRate.toLong() * durationMs / 1000).toInt()
        val out = FloatArray(count)
        if (count == 0 || amplitude <= 0f) return out
        val attack = (sampleRate * ATTACK_MS / 1000).coerceAtLeast(1)
        val release = (sampleRate * RELEASE_MS / 1000).coerceAtLeast(1)
        val gain = amplitude / FluteTimbre.cyclePeak
        for (i in 0 until count) {
            val envelope = min(1.0, min(i.toDouble() / attack, (count - 1 - i).toDouble() / release))
            out[i] = (gain * envelope * FluteTimbre.waveform(2 * PI * frequencyHz * i / sampleRate)).toFloat()
        }
        return out
    }
}
