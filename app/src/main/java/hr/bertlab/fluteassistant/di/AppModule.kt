// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.di

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hr.bertlab.fluteassistant.audio.flute.AudioTrackFluteTonePlayer
import hr.bertlab.fluteassistant.audio.flute.FluteTonePlayer
import hr.bertlab.fluteassistant.audio.metronome.AudioTrackMetronomePlayer
import hr.bertlab.fluteassistant.audio.metronome.MetronomePlayer
import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.audio.tuner.AudioTrackReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.data.settings.SharedPreferencesSettingsStore
import hr.bertlab.fluteassistant.domain.settings.MetronomeSettings
import hr.bertlab.fluteassistant.domain.settings.SettingsStore
import hr.bertlab.fluteassistant.playback.BackgroundPlayback
import hr.bertlab.fluteassistant.playback.ForegroundServiceBackgroundPlayback
import javax.inject.Singleton

/**
 * Everything both build types bind the same way: the audio engines, the settings and the
 * background playback. The logger and the session recorder differ by build type and are in
 * `SessionModule` (`src/debug` and `src/release`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMetronomePlayer(impl: AudioTrackMetronomePlayer): MetronomePlayer

    @Binds
    @Singleton
    abstract fun bindTunerEngine(impl: AudioRecordTunerEngine): TunerEngine

    @Binds
    @Singleton
    abstract fun bindReferenceTonePlayer(impl: AudioTrackReferenceTonePlayer): ReferenceTonePlayer

    @Binds
    @Singleton
    abstract fun bindFluteTonePlayer(impl: AudioTrackFluteTonePlayer): FluteTonePlayer

    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: SharedPreferencesSettingsStore): SettingsStore

    @Binds
    abstract fun bindBackgroundPlayback(impl: ForegroundServiceBackgroundPlayback): BackgroundPlayback

    companion object {
        @Provides
        fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

        /** One for the whole app: both tabs that use the tempo and the beat share it. */
        @Provides
        @Singleton
        fun provideMetronomeSettings(store: SettingsStore): MetronomeSettings = MetronomeSettings(store)
    }
}
