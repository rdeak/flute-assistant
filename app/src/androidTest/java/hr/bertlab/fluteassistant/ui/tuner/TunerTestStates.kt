// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode

/** Test helper: the tuner listening in [mode] with [targetMidi] chosen and [frequency] heard (null for silence). */
internal fun listening(
    frequency: Double?,
    targetMidi: Int = FluteRange.DEFAULT_TARGET,
    mode: TunerMode = TunerMode.TARGET,
) = TunerUiState(
    permissionGranted = true,
    isListening = true,
    reading = frequency?.let { NoteMath.frequencyToNote(it) },
    targetMidi = targetMidi,
    mode = mode,
)
