// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun `croatian locales map to croatian`() {
        assertEquals(AppLanguage.CROATIAN, AppLanguage.fromLocale(Locale("hr")))
        assertEquals(AppLanguage.CROATIAN, AppLanguage.fromLocale(Locale("hr", "HR")))
    }

    @Test
    fun `english and every unsupported language fall back to english`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.ENGLISH))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.UK))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.GERMAN))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.ROOT))
    }

    @Test
    fun `language tags are what the platform expects`() {
        assertEquals("en", AppLanguage.ENGLISH.tag)
        assertEquals("hr", AppLanguage.CROATIAN.tag)
    }
}
