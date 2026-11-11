// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** The musical interpretation of a detected frequency. */
data class NoteReading(
    /** MIDI note number of the nearest note (A4 = 69). */
    val midi: Int,
    /** Deviation from the nearest note in cents, in the range (-50, 50]. */
    val cents: Double,
    /** The measured frequency in Hz. */
    val frequency: Double,
)

/** Equal-temperament conversions between frequency and MIDI number, at concert pitch. */
object NoteMath {
    /** A4 in Hz. */
    private const val A4_HZ = 440.0

    fun frequencyToNote(frequency: Double): NoteReading {
        require(frequency > 0.0) { "frequency must be positive" }
        val exactMidi = 69.0 + 12.0 * log2(frequency / A4_HZ)
        val midi = exactMidi.roundToInt()
        return NoteReading(midi = midi, cents = (exactMidi - midi) * 100.0, frequency = frequency)
    }

    fun midiToFrequency(midi: Int): Double = A4_HZ * 2.0.pow((midi - 69) / 12.0)

    /**
     * Signed distance in cents from the note [targetMidi] to [frequency]: positive when sharp,
     * negative when flat, and not clamped, so a different note is hundreds of cents away.
     */
    fun centsBetween(frequency: Double, targetMidi: Int): Double {
        require(frequency > 0.0) { "frequency must be positive" }
        return 1200.0 * log2(frequency / midiToFrequency(targetMidi))
    }
}
