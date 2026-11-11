// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import hr.bertlab.fluteassistant.audio.metronome.MetronomePlayer
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the metronome alive while the app is off screen.
 *
 * It owns nothing: the engine ([MetronomePlayer]) is an app-wide singleton that the screen
 * starts and stops. The service only promotes the process with a media-playback notification
 * (tempo, meter, Stop action) and watches the engine, winding itself down as soon as playback
 * ends for any reason: Stop in the app or the notification, audio focus lost, headphones
 * unplugged, or the task being swiped away.
 */
@AndroidEntryPoint
class MetronomeService : Service() {

    @Inject
    lateinit var player: MetronomePlayer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private lateinit var notifications: PlaybackNotifications

    override fun onCreate() {
        super.onCreate()
        notifications = PlaybackNotifications(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote first, whatever the intent: a service started with startForegroundService()
        // that does not call startForeground() promptly gets the whole app killed.
        showNotification(player.playback.value?.config)
        if (intent?.action == ACTION_STOP) player.stop()
        if (watcher == null) watcher = scope.launch { followPlayback() }
        return START_NOT_STICKY // a metronome that resurrects itself after a kill would be a surprise
    }

    /** Mirrors the engine: refresh the notification on settings changes, stop when it goes idle. */
    private suspend fun followPlayback() {
        player.playback
            .map { it?.config }
            .distinctUntilChanged()
            .conflate() // a dial drag changes the tempo many times a second: only the latest matters
            .collect { config ->
                if (config == null) {
                    finish()
                } else {
                    showNotification(config)
                    delay(NOTIFICATION_UPDATE_INTERVAL_MS)
                }
            }
    }

    @Suppress("InlinedApi") // the constant is inlined; ServiceCompat ignores the type below API 29
    private fun showNotification(config: MetronomeConfig?) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notifications.build(config),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop() // swiping the app away means "I am done"; the watcher then stops the service
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "hr.bertlab.fluteassistant.action.STOP"
        const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 250L

        fun intent(context: Context, action: String? = null): Intent =
            Intent(context, MetronomeService::class.java).setAction(action)
    }
}
