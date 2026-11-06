// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import android.app.UiAutomation
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.ui.common.AppLanguage
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SideRailTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var selected by mutableStateOf(AppTab.FLUTE)

    private fun show(
        height: Int = 411,
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
        drumBeating: Boolean = false,
        width: Int = 915,
    ) {
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = width, height = height, fontScale = fontScale) {
                    // In a Row as in MainScreen: the host alone would stretch it to its own width.
                    Row {
                        SideRail(
                            selected = selected,
                            onSelect = { selected = it },
                            drumBeating = drumBeating,
                            beatPulse = 0L,
                            modifier = Modifier.testTag(RAIL),
                        )
                    }
                }
            }
        }
    }

    private fun logo() = compose.onNodeWithContentDescription("Flute Assistant")

    /** The bottom is a number, not `assertIsDisplayed`: an upright emulator has room past the forced height. */
    private fun assertTheFlagAndItsShadowEndInside(height: Int, button: String = "Language") {
        val flag = compose.onNodeWithContentDescription(button).assertIsDisplayed()
        val bottom = flag.getUnclippedBoundsInRoot().bottom + FLAG_SHADOW
        assertTrue("the flag ends at $bottom", bottom <= height.dp)
    }

    /**
     * A word cut short still reads as displayed, so this asks the word's own layout. It also asks that
     * the word stands inside its tab: a large emoji over it can push it onto the tab's edge, where the
     * outline and the rounded clip cut it off although its own layout is whole.
     */
    private fun assertWholeWord(word: String) {
        val node = compose.onNodeWithText(word, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("$word is cut short", layouts.single().hasVisualOverflow)
        val tab = compose.onNodeWithText(word).getUnclippedBoundsInRoot()
        val text = node.getUnclippedBoundsInRoot()
        // The open tab's outline is drawn over the edge of its content, so the word must also stay clear of that.
        assertTrue("$word starts at ${text.top}, its tab's outline ends at ${tab.top + OutlineWidth}", text.top >= tab.top + OutlineWidth - 1.dp)
        assertTrue("$word ends at ${text.bottom}, its tab's outline starts at ${tab.bottom - OutlineWidth}", text.bottom <= tab.bottom - OutlineWidth + 1.dp)
    }

    private fun assertEverythingOnTheRail(height: Int, railWidth: Int) {
        show(height = height)
        listOf("Flute", "Metronome", "Tuner", "Notes").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
            assertWholeWord(it)
        }
        logo().assertIsDisplayed()
        assertTheFlagAndItsShadowEndInside(height)
        compose.onNodeWithTag(RAIL).assertWidthIsEqualTo(railWidth.dp)
    }

    @Test
    fun shows_the_four_tabs_the_logo_and_the_flag() = assertEverythingOnTheRail(height = 411, railWidth = 92)

    @Test
    fun shows_the_four_tabs_the_logo_and_the_flag_on_a_short_window() = assertEverythingOnTheRail(height = 360, railWidth = 84)

    @Test
    fun tapping_a_tab_selects_it() {
        show()
        compose.onNodeWithText("Flute").assertIsSelected()
        compose.onNodeWithText("Tuner").assertIsNotSelected().performClick()
        compose.onNodeWithText("Tuner").assertIsSelected()
        compose.onNodeWithText("Flute").assertIsNotSelected()
        assertEquals(AppTab.TUNER, selected)
    }

    @Test
    fun a_screen_reader_hears_the_tabs_as_tabs_now_that_the_title_is_gone() {
        show()
        compose.onNodeWithText("Flute").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    fun the_drum_reads_playing_while_it_beats() {
        show(drumBeating = true)
        compose.onNodeWithText("Metronome")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
    }

    @Test
    fun words_stay_inside_the_rail_in_croatian_at_large_text() {
        show(height = 360, locale = Locale("hr"), fontScale = 1.5f)
        listOf("Flauta", "Metronom", "Štimer", "Note").forEach(::assertWholeWord)
        assertTheFlagAndItsShadowEndInside(height = 360, button = "Jezik")
    }

    @Test
    fun words_stay_inside_the_rail_in_english_at_large_text() {
        show(height = 360, fontScale = 1.5f)
        listOf("Flute", "Metronome", "Tuner", "Notes").forEach(::assertWholeWord)
        assertTheFlagAndItsShadowEndInside(height = 360)
    }

    /**
     * The largest text size, on the narrowest and shortest phone (640 x 360): the rail's words are
     * whole in both languages and the flag still ends inside the rail. The words' floor is in dp, so it
     * does not double with the text, as a floor of 7 sp did ("Metronome" was cut above about 1.7 times).
     */
    @Test
    fun words_stay_inside_the_rail_in_english_at_the_largest_text() {
        show(height = 360, width = 640, fontScale = 2f)
        listOf("Flute", "Metronome", "Tuner", "Notes").forEach(::assertWholeWord)
        assertTheFlagAndItsShadowEndInside(height = 360)
    }

    @Test
    fun words_stay_inside_the_rail_in_croatian_at_the_largest_text() {
        show(height = 360, width = 640, locale = Locale("hr"), fontScale = 2f)
        listOf("Flauta", "Metronom", "Štimer", "Note").forEach(::assertWholeWord)
        assertTheFlagAndItsShadowEndInside(height = 360, button = "Jezik")
    }

    /**
     * The flag is read from the resources the words come from, so it cannot disagree with them: the
     * button's state is the language it shows. (The test's `LocaleProvider` moves the resources with
     * the words, as the activity's do on a turn.)
     */
    @Test
    fun the_flag_names_the_language_of_the_words_in_croatian() {
        show(height = 360, locale = Locale("hr"))
        compose.onNodeWithContentDescription("Jezik")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hrvatski"))
    }

    @Test
    fun the_flag_names_the_language_of_the_words_in_english() {
        show(height = 360)
        compose.onNodeWithContentDescription("Language")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "English"))
    }

    @Test
    fun the_language_list_opens_from_the_rail() {
        show(height = 360)
        compose.onNodeWithContentDescription("Language").performClick()
        AppLanguage.entries.forEach {
            compose.onNodeWithContentDescription(it.endonym).assertIsDisplayed()
            compose.onNodeWithText(it.endonym).assertDoesNotExist()
        }
    }

    /** Above the flag it would cover the tabs, and the menu's own margin would push it onto the flag. */
    @Test
    fun the_language_list_opens_beside_the_rail() {
        show(height = 360)
        compose.onNodeWithContentDescription("Language").performClick()
        val rail = compose.onNodeWithTag(RAIL).fetchSemanticsNode()
        val railEnd = rail.positionOnScreen.x + rail.size.width
        AppLanguage.entries.forEach {
            val flag = compose.onNodeWithContentDescription(it.endonym).fetchSemanticsNode()
            assertTrue("${it.endonym} starts at ${flag.positionOnScreen.x}, the rail ends at $railEnd", flag.positionOnScreen.x > railEnd)
        }
    }

    /**
     * The flag is sized in dp, so large text cannot grow it past its round button: at 1.5 times the
     * text size a flag of 24 sp is 36 dp and its line 43, taller than the short rail's 38 dp button.
     * A text that wants more room than its parent gives is measured at the room it is given, so too
     * big a flag shows as a box exactly as tall as the button's face; a flag that fits stands clear of it.
     */
    private fun assertTheFlagFitsItsButton(height: Int, fontScale: Float) {
        show(height = height, fontScale = fontScale)
        val button = compose.onNodeWithContentDescription("Language", useUnmergedTree = true)
        val face = button.getUnclippedBoundsInRoot()
        val flag = button.onChildren()
        flag.assertCountEquals(1)
        val box = flag[0].getUnclippedBoundsInRoot()
        assertTrue(
            "$height tall at $fontScale: the flag's box is ${box.bottom - box.top} tall, its button's face ${face.bottom - face.top}",
            box.bottom - box.top <= face.bottom - face.top - 2.dp,
        )
    }

    @Test
    fun the_flag_fits_its_button_on_the_short_rail() = assertTheFlagFitsItsButton(height = 360, fontScale = 1f)

    @Test
    fun the_flag_fits_its_button_on_the_short_rail_at_large_text() = assertTheFlagFitsItsButton(height = 360, fontScale = 1.5f)

    @Test
    fun the_flag_fits_its_button_on_the_rail_at_the_largest_text() {
        assertTheFlagFitsItsButton(height = 411, fontScale = 2f)
    }

    // ---- A turn between the two bars ----

    private var sideways by mutableStateOf(true)
    private var creditsOpen by mutableStateOf(false)
    private var languageOpen by mutableStateOf(false)

    /**
     * What `MainScreen` does with the bars: the rail beside the tab when the phone is on its side,
     * the title bar over it when upright, and what is open above both, so a turn between the two
     * does not close it.
     */
    private fun showBothBars(height: Int = 411, locale: Locale = Locale.ENGLISH) {
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(height = height, sideways = sideways) {
                    if (sideways) {
                        Row {
                            SideRail(
                                selected = selected,
                                onSelect = { selected = it },
                                drumBeating = false,
                                beatPulse = 0L,
                                creditsOpen = creditsOpen,
                                onCreditsOpenChange = { creditsOpen = it },
                                languageOpen = languageOpen,
                                onLanguageOpenChange = { languageOpen = it },
                            )
                        }
                    } else {
                        TitleBar(
                            tab = selected,
                            creditsOpen = creditsOpen,
                            onCreditsOpenChange = { creditsOpen = it },
                            languageOpen = languageOpen,
                            onLanguageOpenChange = { languageOpen = it },
                        )
                    }
                }
            }
        }
    }

    private fun turn() {
        compose.runOnIdle { sideways = !sideways }
        compose.waitForIdle()
    }

    private fun holder() = compose.onNodeWithText("Bertlab d.o.o.", substring = true)

    @Test
    fun the_credits_stay_open_when_the_phone_turns() {
        showBothBars()
        logo().performTouchInput { longClick() }
        holder().assertIsDisplayed()
        turn()
        holder().assertIsDisplayed()
        turn()
        holder().assertIsDisplayed()
        // And OK still closes them, for the next turn too.
        compose.onNodeWithText("OK").performClick()
        holder().assertDoesNotExist()
        turn()
        holder().assertDoesNotExist()
    }

    @Test
    fun the_credits_stay_shut_when_the_phone_turns() {
        showBothBars()
        turn()
        holder().assertDoesNotExist()
        turn()
        holder().assertDoesNotExist()
    }

    @Test
    fun the_language_list_stays_open_when_the_phone_turns() {
        showBothBars(height = 360)
        compose.onNodeWithContentDescription("Language").performClick()
        compose.onNodeWithContentDescription("Hrvatski").assertIsDisplayed()
        turn()
        compose.onNodeWithContentDescription("Hrvatski").assertIsDisplayed()
        turn()
        compose.onNodeWithContentDescription("Hrvatski").assertIsDisplayed()
        // Picking the language it is already in closes it, for the next turn too.
        compose.onNodeWithContentDescription("English").performClick()
        compose.onNodeWithContentDescription("Hrvatski").assertDoesNotExist()
        turn()
        compose.onNodeWithContentDescription("Hrvatski").assertDoesNotExist()
    }

    /**
     * The dialog has a window of its own, which the forced size does not reach, so this turns the
     * real window: on an upright emulator the credits would fit and prove nothing.
     */
    @Test
    fun the_credits_scroll_on_a_short_window() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        try {
            compose.waitUntil(ROTATION_TIMEOUT_MS) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            show(height = 360)
            logo().performTouchInput { longClick() }
            val lastLine = compose.onNodeWithText("OK")
            lastLine.assertIsNotDisplayed()
            lastLine.performScrollTo().assertIsDisplayed()
        } finally {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private companion object {
        const val RAIL = "rail"
        const val ROTATION_TIMEOUT_MS = 10_000L

        /** `ChunkyButton`'s default depth. */
        val FLAG_SHADOW = 4.dp
    }
}
