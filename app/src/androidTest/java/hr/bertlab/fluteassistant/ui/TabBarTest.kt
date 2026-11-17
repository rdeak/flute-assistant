// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.UprightHost
import hr.bertlab.fluteassistant.ui.common.assertWholeWord
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The upright tab bar with five tabs (since 2026-11-17): every word whole at every phone width and text size. */
@RunWith(AndroidJUnit4::class)
class TabBarTest {

    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf(AppTab.FLUTE)
    private var width by mutableStateOf(412)
    private var scale by mutableStateOf(1f)
    private var locale by mutableStateOf(Locale.ENGLISH)

    private fun show() {
        compose.setContent {
            LocaleProvider(locale) {
                UprightHost(DpSize(width.dp, 120.dp), scale) {
                    TabBar(selected = selected, onSelect = { selected = it }, drumBeating = false, beatPulse = 0L)
                }
            }
        }
    }

    @Test
    fun shows_the_five_tabs_in_order() {
        show()
        val words = TAB_WORDS
        val tabs = words.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
        tabs.zipWithNext().forEach { (left, right) -> assertTrue("$left before $right", right.left >= left.right) }
    }

    @Test
    fun tapping_fingers_selects_it() {
        show()
        compose.onNodeWithText("Fingers").performClick()
        assertEquals(AppTab.FINGERS, selected)
        compose.onNodeWithText("Fingers").assertIsSelected()
    }

    @Test
    fun every_word_is_whole_at_every_width_and_text_size() {
        show()
        val words = mapOf(
            Locale.ENGLISH to TAB_WORDS,
            Locale("hr") to TAB_WORDS_HR,
        )
        for (w in listOf(360, 412)) for (s in listOf(1f, 1.5f, 2f)) for ((language, list) in words) {
            compose.runOnIdle {
                width = w
                scale = s
                locale = language
            }
            compose.waitForIdle()
            list.forEach { compose.assertWholeWord(it, case = "$w dp, text $s, ${language.language}: ") }
        }
    }
}
