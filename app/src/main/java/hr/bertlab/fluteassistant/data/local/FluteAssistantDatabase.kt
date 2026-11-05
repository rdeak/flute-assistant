// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PresetEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FluteAssistantDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        const val NAME = "flute_assistant.db"
    }
}
