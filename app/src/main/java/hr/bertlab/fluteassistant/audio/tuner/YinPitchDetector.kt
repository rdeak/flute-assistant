// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A fundamental-frequency estimate. [probability] is 1 - (normalised YIN aperiodicity). */
data class PitchEstimate(val frequency: Double, val probability: Double)

/**
 * Monophonic pitch detector based on the YIN algorithm
 * (de Cheveigné & Kawahara, 2002), with two additions:
 *
 * 1. A silence gate on RMS so an idle microphone does not produce random notes.
 * 2. A period refinement step: after YIN finds the period `T`, the raw difference
 *    function is re-evaluated around `k·T` for increasing `k`. Dividing the interpolated
 *    minimum by `k` sharpens the estimate by roughly a factor of `k`, which matters for
 *    high notes where one period spans only ~20 samples.
 *
 * It reads the newest half of the buffer, where the caller appends each hop (`differenceAt`).
 * Until 2026-11-10 it read the oldest half, as YIN is usually written, and so heard every note
 * one frame (43 ms) late: on the phone's recorded "Your turn" sessions of 2026-11-05 the change
 * moved 60 of 66 notes one or two frames earlier and no mark. The listening rules count frames,
 * so check a change here on the recorded sessions (the `sessions` skill), not on tone alone.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-tested on the JVM.
 *
 * [threshold] is the first of two confidence gates: a frame is returned only when the
 * normalised difference dips under it (0.2 means probability >= 0.8), and `TunerViewModel`
 * then applies `MIN_PROBABILITY` on top. Lower makes the detector stricter against breathy
 * input; higher risks the dip at half the period being accepted, an octave-up error on a low
 * note with a strong second harmonic. Change the two together and judge them on noisy real
 * recordings, not clean ones (see `MIN_PROBABILITY`).
 */
class YinPitchDetector(
    private val sampleRate: Int,
    val bufferSize: Int = 4096,
    private val threshold: Double = 0.2,
    minFrequency: Double = 50.0,
    maxFrequency: Double = 3000.0,
    private val silenceRms: Double = 1e-4,
) {
    private val halfSize = bufferSize / 2
    private val tauMin = max(2, floor(sampleRate / maxFrequency).toInt())
    private val tauMax = min(halfSize - 2, ceil(sampleRate / minFrequency).toInt())

    // Scratch buffers reused between calls (not thread-safe; one detector per thread).
    private val difference = DoubleArray(halfSize)
    private val cmnd = DoubleArray(halfSize)

    /**
     * @param buffer exactly [bufferSize] mono samples in [-1, 1], the oldest first.
     * @return the detected pitch, or null for silence / unpitched input.
     */
    fun detect(buffer: FloatArray): PitchEstimate? {
        require(buffer.size == bufferSize) { "expected $bufferSize samples, got ${buffer.size}" }
        if (rms(buffer) < silenceRms) return null

        // Steps 1 + 2: difference function d(tau) over the search range.
        for (tau in 1..tauMax) difference[tau] = differenceAt(buffer, tau)

        // Step 3: cumulative mean normalised difference d'(tau).
        cmnd[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..tauMax) {
            runningSum += difference[tau]
            cmnd[tau] = if (runningSum == 0.0) 1.0 else difference[tau] * tau / runningSum
        }

        // Step 4: absolute threshold - first dip below the threshold, then walk to its local minimum.
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) return null

        // Step 5: parabolic interpolation for sub-sample accuracy.
        val probability = (1.0 - cmnd[found]).coerceIn(0.0, 1.0)
        var period = parabolicMinimum(cmnd, found, 1, tauMax)

        // Step 6: refine using multiples of the period.
        period = refinePeriod(buffer, period)

        return PitchEstimate(frequency = sampleRate / period, probability = probability)
    }

    private fun rms(buffer: FloatArray): Double {
        var energy = 0.0
        for (v in buffer) energy += v.toDouble() * v
        return sqrt(energy / buffer.size)
    }

    /**
     * The squared difference between a stretch of [halfSize] samples and the same stretch [tau]
     * later, the later one ending on the buffer's last sample: the newest sound, which the
     * caller puts at the end.
     */
    private fun differenceAt(x: FloatArray, tau: Int): Double {
        var sum = 0.0
        val end = bufferSize - tau
        for (i in end - halfSize until end) {
            val delta = (x[i] - x[i + tau]).toDouble()
            sum += delta * delta
        }
        return sum
    }

    /**
     * Doubles `k` while `k·period` still fits in the lag range, each time re-centring the
     * estimate on the local minimum of the raw difference function near `k·period`.
     */
    private fun refinePeriod(buffer: FloatArray, initialPeriod: Double): Double {
        var estimate = initialPeriod
        var k = 2
        val maxLag = halfSize - 1
        val window = 3
        val local = DoubleArray(2 * window + 3)
        while (k * estimate + window + 1 <= maxLag) {
            val center = (k * estimate).roundToInt()
            val lo = center - window - 1
            for (i in local.indices) local[i] = differenceAt(buffer, lo + i)

            var best = 1
            for (i in 2 until local.size - 1) if (local[i] < local[best]) best = i
            // A minimum on the window edge means the guess was off; stop refining.
            if (best == 1 || best == local.size - 2) break

            val refinedLag = (lo + best) + parabolicOffset(local[best - 1], local[best], local[best + 1])
            estimate = refinedLag / k
            k *= 2
        }
        return estimate
    }

    private fun parabolicMinimum(values: DoubleArray, index: Int, lo: Int, hi: Int): Double {
        if (index <= lo || index >= hi) return index.toDouble()
        return index + parabolicOffset(values[index - 1], values[index], values[index + 1])
    }

    /** Offset (-0.5..0.5) of the vertex of the parabola through three equally spaced samples. */
    private fun parabolicOffset(s0: Double, s1: Double, s2: Double): Double {
        val denominator = 2.0 * (2.0 * s1 - s2 - s0)
        if (denominator == 0.0) return 0.0
        return ((s2 - s0) / denominator).coerceIn(-0.5, 0.5)
    }

    companion object {
        /**
         * RMS below which a frame is silence: about -60 dBFS, above microphone hiss and a
         * quiet room but well under a softly played note. Checked on a real phone; the
         * emulator has no audio.
         */
        const val QUIET_ROOM_RMS = 1e-3

        /**
         * The detector the tuner uses: it searches one semitone beyond [FluteRange] on either
         * side, so voices and mains hum below the flute cannot register as low notes, and it
         * gates on [QUIET_ROOM_RMS].
         */
        fun forFlute(sampleRate: Int, bufferSize: Int = 4096): YinPitchDetector = YinPitchDetector(
            sampleRate = sampleRate,
            bufferSize = bufferSize,
            minFrequency = NoteMath.midiToFrequency(FluteRange.LOWEST_MIDI - 1),
            maxFrequency = NoteMath.midiToFrequency(FluteRange.HIGHEST_MIDI + 1),
            silenceRms = QUIET_ROOM_RMS,
        )
    }
}
