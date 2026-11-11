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
 */
data class PitchFrame(val frequency: Double?, val probability: Double, val level: Float, val minLevel: Float = level)
