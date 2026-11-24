// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio

/**
 * How loud every tone the app plays is, in 0..1: the tuner's reference note, the notes game's
 * notes and the flute's keys and playback. Fixed: the app has no volume control, the phone's
 * buttons do that.
 */
const val TONE_VOLUME = 0.8f

/** The peak of a tone at full volume: a comfortable level, about where the metronome's clicks sit. */
internal const val TONE_PEAK = 0.6f

/** The peak a tone is rendered at for a [volume] in 0..1. */
internal fun toneAmplitude(volume: Float): Float = TONE_PEAK * volume.coerceIn(0f, 1f)
