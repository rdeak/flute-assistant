// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import android.app.UiAutomation
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.test.onSiblings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.ui.common.AppLanguage
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
import hr.bertlab.fluteassistant.ui.common.assertWholeWord
import hr.bertlab.fluteassistant.ui.common.needsDevice
import org.junit.Assert.assertEquals
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

    /** The rail's height once it is on the screen, for a test that changes it ([railIs]); until then [show]'s own. */
    private var railHeight by mutableStateOf<Int?>(null)

    private fun show(
        height: Int = 411,
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
        drumBeating: Boolean = false,
        width: Int = 915,
    ) {
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = width, height = railHeight ?: height, fontScale = fontScale) {
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

    private fun assertEverythingOnTheRail(height: Int, railWidth: Int) {
        show(height = height)
        TAB_WORDS.forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
            compose.assertWholeWord(it)
        }
        logo().assertIsDisplayed()
        assertTheFlagAndItsShadowEndInside(height)
        compose.onNodeWithTag(RAIL).assertWidthIsEqualTo(railWidth.dp)
    }

    @Test
    fun shows_the_five_tabs_the_logo_and_the_flag() = assertEverythingOnTheRail(height = 411, railWidth = 92)

    @Test
    fun shows_the_five_tabs_the_logo_and_the_flag_on_a_short_window() = assertEverythingOnTheRail(height = 360, railWidth = 84)

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
        TAB_WORDS_HR.forEach { compose.assertWholeWord(it) }
        assertTheFlagAndItsShadowEndInside(height = 360, button = "Jezik")
    }

    @Test
    fun words_stay_inside_the_rail_in_english_at_large_text() {
        show(height = 360, fontScale = 1.5f)
        TAB_WORDS.forEach { compose.assertWholeWord(it) }
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
        TAB_WORDS.forEach { compose.assertWholeWord(it) }
        assertTheFlagAndItsShadowEndInside(height = 360)
    }

    @Test
    fun words_stay_inside_the_rail_in_croatian_at_the_largest_text() {
        show(height = 360, width = 640, locale = Locale("hr"), fontScale = 2f)
        TAB_WORDS_HR.forEach { compose.assertWholeWord(it) }
        assertTheFlagAndItsShadowEndInside(height = 360, button = "Jezik")
    }

    /**
     * A phone on its side with the display size set larger leaves a window only about 320 dp tall: five tabs' cells
     * are then about 40 dp, and the words must still stand clear of the open tab's outline (the final review, 2026-11-17).
     */
    @Test
    fun words_stay_inside_the_rail_on_a_window_320_tall() {
        show(height = 320, width = 640)
        TAB_WORDS.forEach { compose.assertWholeWord(it) }
    }

    @Test
    fun words_stay_inside_the_rail_on_a_window_320_tall_in_croatian_at_the_largest_text() {
        show(height = 320, width = 640, locale = Locale("hr"), fontScale = 2f)
        TAB_WORDS_HR.forEach { compose.assertWholeWord(it) }
    }

    /**
     * The box of a tab's emoji as it is drawn: `boundsInRoot`, which counts the layer a tab draws a too large emoji
     * smaller with, as `getUnclippedBoundsInRoot` does not. The emoji has no semantics of its own but its place beside
     * the word.
     */
    private fun emojiBox(word: String): DpRect {
        val node = compose.onNodeWithText(word, useUnmergedTree = true).onSiblings()[0].fetchSemanticsNode()
        val box = node.boundsInRoot
        return with(node.layoutInfo.density) { DpRect(box.left.toDp(), box.top.toDp(), box.right.toDp(), box.bottom.toDp()) }
    }

    private fun emojiHeight(word: String) = emojiBox(word).let { (it.bottom - it.top).value }

    private fun railIs(height: Int) {
        compose.runOnIdle { railHeight = height }
        compose.waitForIdle()
    }

    /**
     * Each tab fits its emoji to its own cell (since 2026-11-17: the rail used to work the cell out by hand and guess
     * the lines' heights): the emoji starts under the tab's outline and ends where the word starts, whatever the
     * window's height and the text size. `assertWholeWord` holds the word's own end.
     */
    private fun assertTheEmojiAndTheWordShareTheCell(height: Int, fontScale: Float = 1f, locale: Locale = Locale.ENGLISH) {
        show(height = height, width = 640, fontScale = fontScale, locale = locale)
        val words = tabWords(locale)
        words.forEach { word ->
            val tab = compose.onNodeWithText(word).getUnclippedBoundsInRoot()
            val text = compose.onNodeWithText(word, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val emoji = emojiBox(word)
            assertTrue("$height tall: $word's emoji starts at ${emoji.top}, its tab's outline ends at ${tab.top + OutlineWidth}", emoji.top >= tab.top + OutlineWidth - 1.dp)
            assertTrue("$height tall: $word's emoji ends at ${emoji.bottom}, the word starts at ${text.top}", emoji.bottom <= text.top + 1.dp)
            compose.assertWholeWord(word)
        }
    }

    @Test
    fun the_emoji_and_the_word_share_the_cell_at_360() = assertTheEmojiAndTheWordShareTheCell(height = 360)

    @Test
    fun the_emoji_and_the_word_share_the_cell_at_360_at_the_largest_text() = assertTheEmojiAndTheWordShareTheCell(height = 360, fontScale = 2f, locale = Locale("hr"))

    @Test
    fun the_emoji_and_the_word_share_the_cell_at_411_at_the_largest_text() = assertTheEmojiAndTheWordShareTheCell(height = 411, fontScale = 2f)

    @Test
    fun the_emoji_and_the_word_share_the_cell_on_a_window_300_tall() = assertTheEmojiAndTheWordShareTheCell(height = 300)

    /** On a window too short for the rail's cells the emoji gives way to the word. */
    @Test
    fun the_emoji_is_smaller_on_a_window_320_tall() {
        show(height = 399, width = 640)
        val withRoom = emojiHeight("Flute")
        railIs(320)
        val short = emojiHeight("Flute")
        assertTrue("the emoji is $short tall at 320 and $withRoom with room to spare", short < withRoom - 2f)
        assertTrue("the emoji is $short tall, no less than its floor", short >= withRoom * 0.6f - 1f)
    }

    /**
     * The emoji's size, which nothing held (the review of 2026-11-17: the documents said 20 dp on the 360 dp rail and
     * the rail drew 19). Where a cell has room to spare the emoji is its full size, the taller rail's 24 to the short
     * rail's 20. On the phones the rail was drawn for the cell leaves it a hair less: no less than nine tenths at 360
     * (about 19 of the 20) and nineteen twentieths at 411 (about 23.5 of the 24).
     */
    @Test
    fun the_emoji_is_its_size_where_there_is_room_and_a_hair_less_on_the_phones_the_rail_was_drawn_for() {
        show(height = 399, width = 640) // the short rail, with room to spare
        val short = emojiHeight("Flute")
        railIs(360)
        TAB_WORDS.forEach {
            val at360 = emojiHeight(it)
            assertTrue("$it's emoji at 360 is $at360 of $short", at360 >= short * 0.9f && at360 <= short + 0.5f)
        }
        railIs(560) // the taller rail, with room to spare
        val tall = emojiHeight("Flute")
        assertEquals("the taller rail's emoji is 24 to the short rail's 20", short * 24 / 20, tall, 1f)
        railIs(411)
        TAB_WORDS.forEach {
            val at411 = emojiHeight(it)
            assertTrue("$it's emoji at 411 is $at411 of $tall", at411 >= tall * 0.95f && at411 <= tall + 0.5f)
        }
    }

    /**
     * Five tabs (since 2026-11-17) leave the taller rail's cells about 55 dp: its larger emoji and word must still fit
     * them at the largest text, in both languages.
     */
    @Test
    fun words_stay_inside_the_taller_rail_at_the_largest_text() {
        show(height = 411, width = 915, fontScale = 2f)
        TAB_WORDS.forEach { compose.assertWholeWord(it) }
    }

    @Test
    fun words_stay_inside_the_taller_rail_in_croatian_at_the_largest_text() {
        show(height = 411, width = 915, locale = Locale("hr"), fontScale = 2f)
        TAB_WORDS_HR.forEach { compose.assertWholeWord(it) }
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
    private val panels = BarPanels()

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
                                panels = panels,
                            )
                        }
                    } else {
                        TitleBar(
                            tab = selected,
                            dark = false,
                            onDarkChange = {},
                            panels = panels,
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
     * The dialog has a window of its own, which neither the forced size nor the forced font scale
     * reaches, so this turns the real window and sets the phone's own text size: on an upright
     * emulator the credits would fit and prove nothing, and since the licence and privacy lines
     * left them (2026-11-11) they fit a short window at normal text too. The largest text on a
     * phone on its side is the case the scroll is there for. Only a device has that setting.
     */
    @Test
    fun the_credits_scroll_on_a_short_window() {
        needsDevice()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val ownTextSize = shell(automation, "settings get system font_scale")
        shell(automation, "settings put system font_scale $LARGEST_TEXT")
        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        try {
            compose.waitUntil(ROTATION_TIMEOUT_MS) {
                val now = compose.activity.resources.configuration
                now.orientation == Configuration.ORIENTATION_LANDSCAPE && now.fontScale == LARGEST_TEXT
            }
            show(height = 360, fontScale = LARGEST_TEXT)
            logo().performTouchInput { longClick() }
            val lastLine = compose.onNodeWithText("OK")
            lastLine.assertIsNotDisplayed()
            lastLine.performScrollTo().assertIsDisplayed()
        } finally {
            // Back to what the phone had: a run without ANDROID_SERIAL also reaches a real phone.
            shell(
                automation,
                if (ownTextSize == "null" || ownTextSize.isEmpty()) {
                    "settings delete system font_scale"
                } else {
                    "settings put system font_scale $ownTextSize"
                },
            )
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    /** Runs [command] on the device, waits for it to finish and gives back what it printed. */
    private fun shell(automation: UiAutomation, command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().decodeToString().trim()
        }

    private companion object {
        const val RAIL = "rail"
        const val ROTATION_TIMEOUT_MS = 10_000L

        /** The largest text size Android's settings offer. */
        const val LARGEST_TEXT = 2f

        /** `ChunkyButton`'s default depth. */
        val FLAG_SHADOW = 4.dp
    }
}
