// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.playback

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import javax.inject.Inject

/** Starts [MetronomeService], which keeps the process in the foreground until playback ends. */
class ForegroundServiceBackgroundPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
) : BackgroundPlayback {

    override fun start() {
        try {
            ContextCompat.startForegroundService(context, MetronomeService.intent(context))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses foreground services started while the app is in the background.
            // Playback itself is unaffected; it just will not outlive the screen.
            logger.w(LogTags.PLAYBACK, "playback.error", "reason" to "service_refused", "message" to e.message)
        }
    }
}
