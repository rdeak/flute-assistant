// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** The musical interpretation of a detected frequency. */
data class NoteReading(
    /** Note name using sharps, e.g. "A" or "F♯". */
    val name: String,
    /** Scientific pitch notation octave (A4 = 440 Hz, middle C = C4). */
    val octave: Int,
    /** MIDI note number of the nearest note (A4 = 69). */
    val midi: Int,
    /** Deviation from the nearest note in cents, in the range (-50, 50]. */
    val cents: Double,
    /** The measured frequency in Hz. */
    val frequency: Double,
    /** The exact frequency of the nearest note in Hz for the current reference pitch. */
    val targetFrequency: Double,
) {
    val label: String get() = "$name$octave"
}

/** Equal-temperament conversions between frequency, MIDI number and note name. */
object NoteMath {
    const val DEFAULT_A4 = 440.0
    val NOTE_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    fun frequencyToNote(frequency: Double, a4: Double = DEFAULT_A4): NoteReading {
        require(frequency > 0.0) { "frequency must be positive" }
        require(a4 > 0.0) { "reference pitch must be positive" }
        val exactMidi = 69.0 + 12.0 * log2(frequency / a4)
        val midi = exactMidi.roundToInt()
        val cents = (exactMidi - midi) * 100.0
        val pitchClass = midi.mod(12)
        val octave = (midi - pitchClass) / 12 - 1
        return NoteReading(
            name = NOTE_NAMES[pitchClass],
            octave = octave,
            midi = midi,
            cents = cents,
            frequency = frequency,
            targetFrequency = midiToFrequency(midi, a4),
        )
    }

    fun midiToFrequency(midi: Int, a4: Double = DEFAULT_A4): Double =
        a4 * 2.0.pow((midi - 69) / 12.0)

    /**
     * Signed distance in cents from the note [targetMidi] to [frequency]: positive when sharp,
     * negative when flat, and not clamped, so a different note is hundreds of cents away.
     */
    fun centsBetween(frequency: Double, targetMidi: Int, a4: Double = DEFAULT_A4): Double {
        require(frequency > 0.0) { "frequency must be positive" }
        require(a4 > 0.0) { "reference pitch must be positive" }
        return 1200.0 * log2(frequency / midiToFrequency(targetMidi, a4))
    }

    /** "A♯4"-style label for a MIDI note number. */
    fun labelOf(midi: Int): String {
        val pitchClass = midi.mod(12)
        return NOTE_NAMES[pitchClass] + ((midi - pitchClass) / 12 - 1)
    }
}
