// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.settings

import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The metronome's tempo and beat, in the one place they are kept. The metronome tab sets them on
 * its ring, the flute counts its notes in them, sets them in its speed panel and changes them when
 * a book is opened: all of them read [config] and change it through [update], so no screen holds a
 * copy of its own and nobody has to pass a change on to anybody else. One instance for the whole
 * app. It starts from what [store] has, with a tempo stored by an older, wider ring brought onto
 * this one (40 to 200), and every change is stored at once.
 */
class MetronomeSettings(private val store: SettingsStore) {

    private val _config = MutableStateFlow(
        store.loadMetronomeConfig().let { it.copy(bpm = it.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM)) },
    )
    val config: StateFlow<MetronomeConfig> = _config.asStateFlow()

    /** Changes the tempo or the beat; nothing happens, and nothing is stored, when [transform] changes neither. */
    fun update(transform: (MetronomeConfig) -> MetronomeConfig) {
        val before = _config.value
        val after = transform(before)
        if (after == before) return
        _config.value = after
        store.saveMetronomeConfig(after)
    }
}
