// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import hr.bertlab.fluteassistant.domain.model.TimeSignature

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bpm: Int,
    @ColumnInfo(name = "time_signature") val timeSignature: TimeSignature,
    val subdivision: Subdivision,
    val sound: SoundProfile,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
