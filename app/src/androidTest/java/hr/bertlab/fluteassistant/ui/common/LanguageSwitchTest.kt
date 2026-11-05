// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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

    private fun button(name: String = "Language") = compose.onNodeWithContentDescription(name)

    @Test
    fun closed_it_shows_only_the_current_language() {
        show(AppLanguage.ENGLISH)
        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "English"))
        compose.onNodeWithContentDescription("Hrvatski").assertDoesNotExist()
    }

    @Test
    fun tapping_the_button_lists_both_flags_with_the_current_one_selected() {
        show(AppLanguage.ENGLISH)
        button().performClick()
        compose.onNodeWithContentDescription("English").assertIsSelected()
        compose.onNodeWithContentDescription("Hrvatski").assertIsNotSelected()
        compose.onNodeWithText(AppLanguage.CROATIAN.flag).assertExists()
    }

    @Test
    fun the_list_has_flags_and_no_words() {
        show(AppLanguage.ENGLISH)
        button().performClick()
        compose.onNodeWithText("English").assertDoesNotExist()
        compose.onNodeWithText("Hrvatski").assertDoesNotExist()
    }

    @Test
    fun tapping_the_other_language_reports_it() {
        show(AppLanguage.ENGLISH)
        button().performClick()
        compose.onNodeWithContentDescription("Hrvatski").performClick()
        assertEquals(AppLanguage.CROATIAN, chosen)
    }

    @Test
    fun tapping_the_other_language_closes_the_list() {
        show(AppLanguage.ENGLISH)
        button().performClick()
        compose.onNodeWithContentDescription("Hrvatski").performClick()
        compose.onNodeWithContentDescription("Hrvatski").assertDoesNotExist()
    }

    @Test
    fun tapping_the_current_language_changes_nothing() {
        show(AppLanguage.CROATIAN, Locale("hr"))
        button("Jezik").performClick()
        compose.onNodeWithContentDescription("Hrvatski").assertIsSelected().performClick()
        assertNull(chosen)
    }

    @Test
    fun tapping_the_current_language_closes_the_list() {
        show(AppLanguage.CROATIAN, Locale("hr"))
        button("Jezik").performClick()
        compose.onNodeWithContentDescription("Hrvatski").performClick()
        compose.onNodeWithContentDescription("Hrvatski").assertDoesNotExist()
    }

    @Test
    fun the_button_is_named_in_the_current_language() {
        show(AppLanguage.CROATIAN, Locale("hr"))
        button("Jezik").assertExists()
    }
}
