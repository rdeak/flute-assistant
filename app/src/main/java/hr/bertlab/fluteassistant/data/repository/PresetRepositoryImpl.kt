// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.data.repository

import hr.bertlab.fluteassistant.data.local.PresetDao
import hr.bertlab.fluteassistant.data.local.PresetEntity
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.Preset
import hr.bertlab.fluteassistant.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepositoryImpl @Inject constructor(
    private val dao: PresetDao,
) : PresetRepository {

    override fun observePresets(): Flow<List<Preset>> =
        dao.observeAll().map { rows -> rows.map(PresetEntity::toDomain) }

    override suspend fun save(name: String, config: MetronomeConfig): Long =
        dao.insert(
            PresetEntity(
                name = name,
                bpm = config.bpm,
                timeSignature = config.timeSignature,
                subdivision = config.subdivision,
                sound = config.sound,
                createdAt = System.currentTimeMillis(),
            ),
        )

    override suspend fun delete(id: Long) = dao.deleteById(id)
}

internal fun PresetEntity.toDomain(): Preset = Preset(
    id = id,
    name = name,
    config = MetronomeConfig(
        timeSignature = timeSignature,
        subdivision = subdivision,
        sound = sound,
    ).withBpm(bpm), // clamp: a tampered backup must not reach the audio thread
    createdAt = createdAt,
)
