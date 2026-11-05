// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.usecase

import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.Preset
import hr.bertlab.fluteassistant.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePresetsUseCase @Inject constructor(private val repository: PresetRepository) {
    operator fun invoke(): Flow<List<Preset>> = repository.observePresets()
}

class SavePresetUseCase @Inject constructor(private val repository: PresetRepository) {
    /** Trims the name and refuses blank names so the UI can rely on a simple contract. */
    suspend operator fun invoke(name: String, config: MetronomeConfig): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Preset name must not be blank" }
        return repository.save(trimmed, config)
    }
}

class DeletePresetUseCase @Inject constructor(private val repository: PresetRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
