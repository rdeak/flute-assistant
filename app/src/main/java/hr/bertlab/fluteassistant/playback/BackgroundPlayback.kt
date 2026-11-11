// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.playback

/**
 * Keeps metronome playback alive while the app is not on screen.
 *
 * On Android this means promoting the process with a foreground service and a notification;
 * the interface hides that from the ViewModel so it stays a plain JVM class in tests. Stopping
 * is not part of the contract: the implementation watches the engine and winds itself down
 * whenever playback ends, whoever ended it.
 */
interface BackgroundPlayback {
    /** Call right after a playback session has started. Safe to call again while playing. */
    fun start()
}
