// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.di

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hr.bertlab.fluteassistant.audio.metronome.AudioTrackMetronomePlayer
import hr.bertlab.fluteassistant.audio.flute.AudioTrackFluteTonePlayer
import hr.bertlab.fluteassistant.audio.flute.FluteTonePlayer
import hr.bertlab.fluteassistant.audio.metronome.MetronomePlayer
import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.audio.tuner.AudioTrackReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.MonotonicClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

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

    companion object {
        @Provides
        fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
    }
}
