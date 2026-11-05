// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.repository

import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.Preset
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for presets. Implemented by the data layer (Room). */
interface PresetRepository {
    /** Emits the full preset list whenever it changes, newest first. */
    fun observePresets(): Flow<List<Preset>>

    /** Persists [config] under [name] and returns the new preset id. */
    suspend fun save(name: String, config: MetronomeConfig): Long

    suspend fun delete(id: Long)
}
