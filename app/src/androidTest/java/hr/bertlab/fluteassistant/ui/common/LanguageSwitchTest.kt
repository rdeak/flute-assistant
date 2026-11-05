// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    private var chosen: AppLanguage? = null

    private fun show(current: AppLanguage, locale: Locale = Locale.ENGLISH) {
        compose.setContent {
            LocaleProvider(locale) {
                LanguageSwitch(current = current, onSelect = { chosen = it })
            }
        }
    }

    @Test
    fun the_current_language_is_selected_and_labelled_in_its_own_language() {
        show(AppLanguage.ENGLISH)
        compose.onNodeWithContentDescription("English").assertIsSelected()
        compose.onNodeWithContentDescription("Hrvatski").assertIsNotSelected()
        compose.onNodeWithText(AppLanguage.CROATIAN.flag).assertExists()
    }

    @Test
    fun tapping_the_other_language_reports_it() {
        show(AppLanguage.ENGLISH)
        compose.onNodeWithContentDescription("Hrvatski").performClick()
        assertEquals(AppLanguage.CROATIAN, chosen)
    }

    @Test
    fun tapping_the_current_language_changes_nothing() {
        show(AppLanguage.CROATIAN, Locale("hr"))
        compose.onNodeWithContentDescription("Hrvatski").assertIsSelected().performClick()
        assertNull(chosen)
    }

    @Test
    fun the_group_is_named_in_the_current_language() {
        show(AppLanguage.CROATIAN, Locale("hr"))
        compose.onNodeWithContentDescription("Jezik").assertExists()
    }
}
