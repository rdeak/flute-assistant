// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.settings

import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class MetronomeSettingsTest {

    @Test
    fun `it starts from what is stored`() {
        val stored = MetronomeConfig(bpm = 96, timeSignature = TimeSignature.THREE_FOUR)
        assertEquals(stored, MetronomeSettings(FakeSettingsStore(config = stored)).config.value)
    }

    @Test
    fun `a tempo stored by a wider ring is brought onto this one, and the store is left as it was`() {
        val store = FakeSettingsStore(config = MetronomeConfig(bpm = 250))
        assertEquals(TempoBand.MAX_BPM, MetronomeSettings(store).config.value.bpm)
        assertEquals(250, store.config.bpm)
        assertEquals(TempoBand.MIN_BPM, MetronomeSettings(FakeSettingsStore(config = MetronomeConfig(bpm = 30))).config.value.bpm)
    }

    @Test
    fun `a change is shown and stored at once`() {
        val store = FakeSettingsStore()
        val settings = MetronomeSettings(store)
        settings.update { it.withBpm(72).copy(timeSignature = TimeSignature.SIX_EIGHT) }
        assertEquals(MetronomeConfig(bpm = 72, timeSignature = TimeSignature.SIX_EIGHT), settings.config.value)
        assertEquals(settings.config.value, store.config)
    }

    @Test
    fun `a change that changes nothing is not stored`() {
        val store = FakeSettingsStore(config = MetronomeConfig(bpm = 250))
        val settings = MetronomeSettings(store)
        settings.update { it.withBpm(TempoBand.MAX_BPM) }
        assertEquals("the store still holds what it had", 250, store.config.bpm)
    }
}
