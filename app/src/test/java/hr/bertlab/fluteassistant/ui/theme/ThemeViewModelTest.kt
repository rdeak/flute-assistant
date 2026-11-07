// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.theme

import hr.bertlab.fluteassistant.domain.model.ThemeChoice
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeViewModelTest {

    private val settings = FakeSettingsStore()

    @Test
    fun `starts from the stored choice`() {
        settings.theme = ThemeChoice.DARK
        assertEquals(ThemeChoice.DARK, ThemeViewModel(settings).choice.value)
    }

    @Test
    fun `a fresh install follows the phone`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeViewModel(settings).choice.value)
    }

    @Test
    fun `choosing dark is remembered`() {
        val viewModel = ThemeViewModel(settings)
        viewModel.setDark(true)
        assertEquals(ThemeChoice.DARK, viewModel.choice.value)
        assertEquals(ThemeChoice.DARK, settings.theme)
    }

    @Test
    fun `choosing light is remembered`() {
        settings.theme = ThemeChoice.DARK
        val viewModel = ThemeViewModel(settings)
        viewModel.setDark(false)
        assertEquals(ThemeChoice.LIGHT, viewModel.choice.value)
        assertEquals(ThemeChoice.LIGHT, settings.theme)
    }
}
