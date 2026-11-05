// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The app's flute-like voice: a sine with a soft second and third harmonic. Shared by the
 * tuner's reference tone and the flute tab's sustained tone so both sound the same. Pure Kotlin.
 */
object FluteTimbre {
    private val HARMONICS = doubleArrayOf(1.0, 0.3, 0.1)

    /** Peak of one cycle of the harmonic sum, so the output can be scaled to peak exactly at an amplitude. */
    val cyclePeak: Double = (0 until 2048).maxOf { i -> abs(waveform(2 * PI * i / 2048)) }

    /** The waveform at [phase] radians, unscaled (peaks at [cyclePeak]). */
    fun waveform(phase: Double): Double {
        var v = 0.0
        for (k in HARMONICS.indices) v += HARMONICS[k] * sin((k + 1) * phase)
        return v
    }
}
