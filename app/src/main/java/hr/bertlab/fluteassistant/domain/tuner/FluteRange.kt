// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

/**
 * The notes a flute can play and the tuner offers as targets: c1 to c4 in the Croatian
 * music-school naming (C4 to C7 in scientific pitch notation), every semitone.
 */
object FluteRange {
    /** c1 / C4, middle C. */
    const val LOWEST_MIDI = 60
    /** c4 / C7. */
    const val HIGHEST_MIDI = 96
    /** a1 / A4, the note an orchestra tunes to. */
    const val DEFAULT_TARGET = 69
    /**
     * a1 / A4, the note the head joint is set on: the tuner names the head joint only when the
     * child tunes on it (spec `2026-11-14-tuning-guide-design.md`). The same note as
     * [DEFAULT_TARGET] but its own constant, so a new default never moves the tuning note.
     */
    const val TUNING_NOTE = 69

    val midiRange: IntRange = LOWEST_MIDI..HIGHEST_MIDI

    fun clamp(midi: Int): Int = midi.coerceIn(midiRange)
}
