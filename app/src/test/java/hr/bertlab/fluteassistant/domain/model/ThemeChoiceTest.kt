// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeChoiceTest {

    @Test
    fun `system follows the phone`() {
        assertTrue(ThemeChoice.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeChoice.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun `light is light whatever the phone says`() {
        assertFalse(ThemeChoice.LIGHT.isDark(systemDark = true))
        assertFalse(ThemeChoice.LIGHT.isDark(systemDark = false))
    }

    @Test
    fun `dark is dark whatever the phone says`() {
        assertTrue(ThemeChoice.DARK.isDark(systemDark = true))
        assertTrue(ThemeChoice.DARK.isDark(systemDark = false))
    }
}
