// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.model

/** A named, saved metronome configuration (e.g. "Song 1 - Live"). */
data class Preset(
    val id: Long,
    val name: String,
    val config: MetronomeConfig,
    val createdAt: Long,
)
