// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

/**
 * One flute voice for the flute tab: a note sounds from [noteOn] until [noteOff], and a new
 * [noteOn] takes the voice over. [open] is called when the tab appears, so the output is
 * already running (silent) and the first press is not late; [close] when the tab is no longer
 * visible, so nothing plays or drains the battery in the background. [noteOn] on a closed
 * player opens it first, so a late [open] is never fatal. Every call returns at once.
 */
interface FluteTonePlayer {
    /** Starts the output, silent; idempotent. */
    fun open()

    /** Sounds [frequencyHz] at [volume] (0..1) until [noteOff]; while a note sounds, the new one takes over. */
    fun noteOn(frequencyHz: Double, volume: Float)

    /** Fades the sounding note out; safe to call when nothing sounds. */
    fun noteOff()

    /** Silences the voice and releases the output; idempotent. */
    fun close()
}
