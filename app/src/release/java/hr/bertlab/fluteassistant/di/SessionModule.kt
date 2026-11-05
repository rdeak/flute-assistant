// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hr.bertlab.fluteassistant.audio.tuner.AudioTap
import hr.bertlab.fluteassistant.core.log.AndroidLogger
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.ui.flute.ListenRecorder
import javax.inject.Singleton

/**
 * The release build's logging and session wiring: the plain [AndroidLogger], and nothing
 * recorded. The debug build's module of the same name (`src/debug`) records every "Your turn"
 * run instead (`ui/flute/CLAUDE.md`, "Sessions"); this build compiles none of that code.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideLogger(@ApplicationContext context: Context): Logger =
        AndroidLogger(debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)

    @Provides
    fun provideAudioTap(): AudioTap = AudioTap.None

    @Provides
    fun provideListenRecorder(): ListenRecorder = ListenRecorder.None
}
