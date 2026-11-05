// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hr.bertlab.fluteassistant.data.repository.PresetRepositoryImpl
import hr.bertlab.fluteassistant.data.settings.SharedPreferencesSettingsStore
import hr.bertlab.fluteassistant.domain.repository.PresetRepository
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPresetRepository(impl: PresetRepositoryImpl): PresetRepository

    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: SharedPreferencesSettingsStore): SettingsStore
}
