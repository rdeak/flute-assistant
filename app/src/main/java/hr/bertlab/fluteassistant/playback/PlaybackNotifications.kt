// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hr.bertlab.fluteassistant.MainActivity
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.ui.common.summary

/** Builds the ongoing "metronome running" notification shown by [MetronomeService]. */
internal class PlaybackNotifications(private val context: Context) {

    init {
        // Creating an existing channel is a no-op, so this is safe on every service start.
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_playback))
                .setDescription(context.getString(R.string.notification_channel_playback_description))
                .setShowBadge(false)
                .build(),
        )
    }

    fun build(config: MetronomeConfig?): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = MetronomeService.intent(context, MetronomeService.ACTION_STOP)
        val stop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, REQUEST_STOP, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(context, REQUEST_STOP, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_metronome)
            .setContentTitle(context.getString(R.string.notification_playing_title))
            .setContentText(config?.summary(context.resources).orEmpty())
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stat_stop, context.getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val REQUEST_OPEN = 0
        const val REQUEST_STOP = 1
    }
}
