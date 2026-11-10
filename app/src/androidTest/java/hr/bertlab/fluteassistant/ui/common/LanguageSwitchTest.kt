// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
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

    private fun pixels(node: SemanticsNodeInteraction) = node.captureToImage().toPixelMap().buffer.copyOf()

    /**
     * A flag is a picture and not a word, and at the largest text size it would outgrow its circle:
     * the button and a choice in the list draw the same at any text size. The choice stands on its
     * own here, as a font scale set in a test does not reach a popup's window.
     */
    @Test
    fun the_flags_do_not_grow_with_the_text_size() {
        needsDevice()
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                LocaleProvider(Locale.ENGLISH) {
                    Column {
                        LanguageSwitch(current = AppLanguage.ENGLISH, onSelect = {})
                        FlagChoice(AppLanguage.CROATIAN, selected = false, onClick = {})
                    }
                }
            }
        }
        val choice = compose.onNodeWithContentDescription("Hrvatski")
        val buttonBefore = pixels(button())
        val choiceBefore = pixels(choice)
        compose.runOnIdle { fontScale = 2f }
        compose.waitForIdle()
        assertArrayEquals("the button's flag changed", buttonBefore, pixels(button()))
        assertArrayEquals("the choice's flag changed", choiceBefore, pixels(choice))
    }

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
