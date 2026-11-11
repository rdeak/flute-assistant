// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * One analysis window of microphone input.
 * @property frequency detected fundamental in Hz, or null when nothing pitched was found.
 * @property probability confidence in [0, 1] (0 when [frequency] is null).
 * @property level input level in [0, 1] for a simple meter (`FrameLevels.level`).
 * @property minLevel the quietest short stretch inside the newest hop (`FrameLevels.minLevel`),
 * which a tongued stop between two notes of one pitch drops where [level] barely moves; the
 * window's [level] when nothing finer is known.
 * @property hopMs how far the window advanced to this frame, in ms: `AudioRecordTunerEngine`'s hop at
 * the rate the microphone opened at (42.7 ms at 48 kHz, 46.4 ms at 44.1 kHz). "Your turn" times a
 * note's length by adding these up, so a replay of the same frames times it the same way.
 */
data class PitchFrame(
    val frequency: Double?,
    val probability: Double,
    val level: Float,
    val minLevel: Float = level,
    val hopMs: Double = DEFAULT_HOP_MS,
) {
    companion object {
        /** The hop of 2048 samples at 48 kHz, the rate the engine asks for first. */
        const val DEFAULT_HOP_MS = 2048 * 1000.0 / 48_000
    }
}
