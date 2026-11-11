// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

/** Plays one steady reference note through the speaker so the child can hear what to aim for. */
interface ReferenceTonePlayer {
    /** Starts a [durationMs] long tone at [frequencyHz]; [volume] is 0..1. Replaces any tone still playing. */
    fun play(frequencyHz: Double, durationMs: Int, volume: Float)

    /** Silences the tone early; safe to call when nothing is playing. */
    fun stop()
}
