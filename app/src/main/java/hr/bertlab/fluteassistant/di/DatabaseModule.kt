// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hr.bertlab.fluteassistant.data.local.FluteAssistantDatabase
import hr.bertlab.fluteassistant.data.local.PresetDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FluteAssistantDatabase =
        Room.databaseBuilder(context, FluteAssistantDatabase::class.java, FluteAssistantDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePresetDao(database: FluteAssistantDatabase): PresetDao = database.presetDao()
}
