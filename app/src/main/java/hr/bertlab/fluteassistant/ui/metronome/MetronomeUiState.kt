// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import hr.bertlab.fluteassistant.domain.metronome.BeatStrength
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig

/** One-shot messages the screen shows in a snackbar (mapped to string resources in the UI). */
enum class MetronomeMessage { AUDIO_UNAVAILABLE }

/** Immutable snapshot of everything the metronome screen renders. */
data class MetronomeUiState(
    val config: MetronomeConfig = MetronomeConfig(),
    val isPlaying: Boolean = false,
    /** Index of the beat currently sounding, or -1 while stopped. */
    val currentBeat: Int = -1,
    val currentStrength: BeatStrength? = null,
    /** Increments on every beat; the UI keys its flash animation on it. */
    val beatPulse: Long = 0L,
    val userMessage: MetronomeMessage? = null,
)
