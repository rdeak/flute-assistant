// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.AppTab
import hr.bertlab.fluteassistant.ui.TitleBar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ThemeSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    private var chosen: Boolean? = null

    private fun show(dark: Boolean, locale: Locale = Locale.ENGLISH) {
        compose.setContent {
            LocaleProvider(locale) {
                ThemeSwitch(dark = dark, onDarkChange = { chosen = it })
            }
        }
    }

    private fun button(name: String = "Dark theme") = compose.onNodeWithContentDescription(name)

    private fun pixels(node: SemanticsNodeInteraction) = node.captureToImage().toPixelMap().buffer.copyOf()

    /**
     * A moon by day and a sun by night. The picture is an emoji, decorative and so hidden from the
     * semantics tree like every other emoji in the app, so this compares the pixels: the button
     * must not look the same in the two states.
     */
    @Test
    fun the_picture_changes_between_day_and_night() {
        needsDevice()
        var dark by mutableStateOf(false)
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                ThemeSwitch(dark = dark, onDarkChange = {})
            }
        }
        val day = pixels(button())
        compose.runOnIdle { dark = true }
        compose.waitForIdle()
        val night = pixels(button())
        assertFalse("the button looks the same by day and by night", day.contentEquals(night))
    }

    @Test
    fun tapping_the_moon_asks_for_night() {
        show(dark = false)
        button().performClick()
        assertEquals(true, chosen)
    }

    @Test
    fun tapping_the_sun_asks_for_day() {
        show(dark = true)
        button().performClick()
        assertEquals(false, chosen)
    }

    @Test
    fun a_screen_reader_hears_a_switch_that_is_off_in_daylight() {
        show(dark = false)
        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()
    }

    @Test
    fun a_screen_reader_hears_a_switch_that_is_on_at_night() {
        show(dark = true)
        button().assertIsOn()
    }

    @Test
    fun the_button_is_named_in_the_current_language() {
        show(dark = false, locale = Locale("hr"))
        button("Tamna tema").assertExists()
    }

    /** Like the flag: a picture in a round button, sized in dp so large text cannot push it out of its circle. */
    @Test
    fun the_moon_does_not_grow_with_the_text_size() {
        needsDevice()
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                LocaleProvider(Locale.ENGLISH) {
                    ThemeSwitch(dark = false, onDarkChange = {})
                }
            }
        }
        val before = pixels(button())
        compose.runOnIdle { fontScale = 2f }
        compose.waitForIdle()
        assertArrayEquals("the moon changed", before, pixels(button()))
    }

    @Test
    fun the_title_bar_puts_the_switch_just_before_the_flag() {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                TitleBar(tab = AppTab.FLUTE, dark = false, onDarkChange = { chosen = it })
            }
        }
        val moon = button().getUnclippedBoundsInRoot()
        val flag = compose.onNodeWithContentDescription("Language").getUnclippedBoundsInRoot()
        assertTrue("the moon ends at ${moon.right}, the flag starts at ${flag.left}", moon.right < flag.left)
        assertTrue("the gap is ${flag.left - moon.right}", flag.left - moon.right <= 12.dp)
        button().performClick()
        assertEquals(true, chosen)
    }
}
