// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A click that has been queued to the audio output.
 * @property audibleAtNanos the [System.nanoTime] at which the click will actually be heard,
 * so the UI can flash exactly when the sound comes out of the speaker rather than when it was
 * written into the (latency-adding) audio buffer.
 * @property sessionId the playback session (see [MetronomePlayer.start]) this click belongs to,
 * so consumers can discard events still in flight from a session that has already stopped.
 */
data class BeatEvent(val click: ClickEvent, val audibleAtNanos: Long, val sessionId: Long)

/** What the engine is playing right now. */
data class PlaybackState(val sessionId: Long, val config: MetronomeConfig, val volume: Float)

/** The metronome audio engine. All methods are safe to call from the main thread and never block. */
interface MetronomePlayer {
    /** Hot stream of clicks as they are scheduled; the UI uses it for the visual indicator. */
    val beatEvents: Flow<BeatEvent>

    /**
     * The current session with its settings, or null while idle. Lets a controller created
     * later (a new screen after the old one was closed, the background service) pick up a
     * session that is already running. For the end of a session rely on [sessionEnded].
     */
    val playback: StateFlow<PlaybackState?>

    /**
     * Emits a session id whenever that session ends, for any reason: [stop], audio focus lost,
     * headphones unplugged, or the output failed. An event stream (not a state) so that a
     * session that starts and fails within the same frame is never conflated away.
     */
    val sessionEnded: Flow<Long>

    /**
     * Starts playback from beat 1 with the given settings (just applies them if already playing).
     * @return the session id tagged on every [BeatEvent] of this run, or [NO_SESSION] if the
     * engine could not start (for example, audio focus was refused during a phone call).
     */
    fun start(config: MetronomeConfig, volume: Float): Long

    /** Applies new settings without interrupting playback; the next click is re-timed if needed. */
    fun update(config: MetronomeConfig)

    /** Master volume in [0, 1]. */
    fun setVolume(volume: Float)

    fun stop()

    companion object {
        const val NO_SESSION = -1L
    }
}
