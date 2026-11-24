// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.ui.common.FingeringGeometry
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.UprightHost
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.common.isInside
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Fingers tab upright (spec `2026-11-17-fingers-tab-design.md`, part 3; the row since `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 3). */
@RunWith(AndroidJUnit4::class)
class FingersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val letters = mutableListOf<Int>()
    private var signs = 0
    private val tuned = mutableListOf<Int>()
    private var scale by mutableStateOf(1f)

    private fun show(state: FingerPick, locale: Locale = Locale.ENGLISH, width: Int = 411, height: Int = 653, fontScale: Float = 1f) {
        scale = fontScale
        compose.setContent {
            LocaleProvider(locale) {
                UprightHost(DpSize(width.dp, height.dp), scale) {
                    FingersContent(state, onLetter = { letters += it }, onSign = { signs++ }, onTune = { tuned += it })
                }
            }
        }
    }

    /**
     * Where the node described [description] is: the chart itself, not the row it belongs to. The whole row is a button
     * since 2026-11-24, so its chart's description, pill and names merge into it; the unmerged tree keeps each where it is.
     */
    private fun bounds(description: String) =
        compose.onNodeWithContentDescription(description, useUnmergedTree = true).getUnclippedBoundsInRoot()


    /**
     * The whole white row is the button (the user, 2026-11-24: "make whole white rectangle as link for opening tuner
     * tune view"): a tap on its staff, at its left end, or on its pill asks for the row's note.
     */
    @Test
    fun a_tap_anywhere_on_a_row_asks_for_its_note_in_the_tuner() {
        show(FingerPick(1)) // C♯: its rows C♯4, C♯5, C♯6
        val rows = compose.onAllNodesWithTag("fingers_row")
        rows[1].assertHasClickAction().performTouchInput { click(Offset(24.dp.toPx(), height / 2f)) }
        rows[0].performTouchInput { click(Offset(width - 14.dp.toPx(), height - 8.dp.toPx())) }
        compose.onNodeWithText("C♯6", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(73, 61, 85), tuned) }
    }

    /** A tap on a row's flute asks for that note in the tuner (the user, 2026-11-18); MainScreen opens it there. */
    @Test
    fun a_tap_on_a_flute_asks_for_its_note_in_the_tuner() {
        show(FingerPick(1)) // C♯: its rows C♯4, C♯5, C♯6
        compose.onNodeWithContentDescription("Fingering for C♯5").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Fingering for C♯4").performClick()
        assertEquals(listOf(73, 61), tuned)
    }

    @Test
    fun it_opens_on_c_with_four_rows() {
        show(FingerPick())
        val rows = listOf("C4", "C5", "C6", "C7").map { bounds("Fingering for $it") }
        rows.zipWithNext().forEach { (upper, lower) -> assertTrue("$upper over $lower", lower.top >= upper.bottom) }
        compose.onNodeWithContentDescription("C").assertIsSelected()
        compose.onAllNodesWithText("Left hand", useUnmergedTree = true).assertCountEquals(4)
        compose.onAllNodesWithText("Right hand", useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    fun another_note_has_three_rows() {
        show(FingerPick(9))
        listOf("A4", "A5", "A6").forEach { compose.onNodeWithContentDescription("Fingering for $it").assertExists() }
        compose.onNodeWithContentDescription("Fingering for A7").assertDoesNotExist()
    }

    @Test
    fun a_letter_key_reports_its_letter() {
        show(FingerPick())
        compose.onNodeWithContentDescription("D").performClick()
        assertEquals(listOf(1), letters)
    }

    @Test
    fun the_sign_key_reports_a_tap() {
        show(FingerPick())
        compose.onNodeWithContentDescription(SIGN).performClick()
        assertEquals(1, signs)
    }

    @Test
    fun while_the_sign_waits_d_and_a_are_off() {
        show(FingerPick(0, true))
        listOf("C♯", "E♭", "F♯", "G♯", "B♭").forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("D").assertIsNotEnabled()
        compose.onNodeWithContentDescription("A").assertIsNotEnabled()
        compose.onNodeWithContentDescription("D").performClick()
        assertTrue("a faded key reported $letters", letters.isEmpty())
        compose.onNodeWithContentDescription(SIGN).assertIsSelected()
    }

    @Test
    fun a_black_note_on_the_screen_names_its_key() {
        show(FingerPick(1))
        compose.onNodeWithContentDescription("C♯").assertIsSelected()
        compose.onNodeWithContentDescription("Fingering for C♯4").assertExists()
        listOf("D", "E", "F", "G", "A", "B").forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    /**
     * The chosen key is a button a screen reader can press again (C♯ back to C), heard as selected; the others are
     * plain buttons with no "not selected" on every visit. A selected radio button is not pressable for TalkBack.
     */
    @Test
    fun the_chosen_key_is_a_button_a_screen_reader_can_press_again() {
        show(FingerPick(1))
        compose.onNodeWithContentDescription("C♯")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsSelected()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(0), letters)
        compose.onNodeWithContentDescription("D")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun four_rows_and_the_keys_fit_the_a55() {
        show(FingerPick())
        val room = compose.onNodeWithTag("fingers_rows").getUnclippedBoundsInRoot()
        val keyRow = compose.onNodeWithTag("fingers_keys").getUnclippedBoundsInRoot()
        compose.onAllNodesWithTag("fingers_row").assertCountEquals(4)
        (0 until 4).forEach { i ->
            val row = compose.onAllNodesWithTag("fingers_row")[i].getUnclippedBoundsInRoot()
            assertTrue("row $i $row is inside $room", row.isInside(room))
        }
        assertTrue("C7 ends at ${bounds("Fingering for C7").bottom}, the keys start at ${keyRow.top}", bounds("Fingering for C7").bottom <= keyRow.top)
        assertTrue("the keys end at ${keyRow.bottom}, the A55's tab at 653", keyRow.bottom <= 653.dp + SLACK)
        val each = KEY_NAMES.map { bounds(it) }
        each.forEach { assertEquals("a key's height", 58f, (it.bottom - it.top).value, 1f) }
        each.zipWithNext().forEach { (left, right) -> assertEquals("the gap between keys", 6f, (right.left - left.right).value, 1f) }
    }

    /**
     * A wide and short upright tab (a near-square foldable's inner screen, where the tab does not zoom): the flute
     * does not fill the width there, or its rows would outgrow the room and C7 would open out of sight (the review
     * of 2026-11-17: the rows' height followed the width, with no cap).
     */
    @Test
    fun four_rows_fit_a_wide_and_short_tab() {
        show(FingerPick(), width = 700, height = 620)
        val room = compose.onNodeWithTag("fingers_rows").getUnclippedBoundsInRoot()
        compose.onAllNodesWithTag("fingers_row").assertCountEquals(4)
        (0 until 4).forEach { i ->
            val row = compose.onAllNodesWithTag("fingers_row")[i].getUnclippedBoundsInRoot()
            assertTrue("row $i $row is inside $room", row.isInside(room))
        }
        // The staff and the flute stand in the middle of the card, as on a wide phone on its side.
        val row = compose.onAllNodesWithTag("fingers_row")[0].getUnclippedBoundsInRoot()
        val flute = bounds("Fingering for C4")
        assertTrue("the flute is ${flute.right - flute.left} wide in a row ${row.right - row.left} wide", flute.right - flute.left < (row.right - row.left) * 0.8f)
        assertTrue("the flute ends at ${flute.right}, its row at ${row.right}", row.right - flute.right > 40.dp)
    }

    /**
     * The hands' names stand under the chart, each inside its hand's bracket, whatever the row's width (spec
     * 2026-11-24, part 3): "Left hand" from the thumb keys to the G sharp lever, "Right hand" from the side B flat lever
     * to the C key, the two apart. They used to be pinned to the row's ends and stood by the wrong keys on a tablet's
     * wider row (the review of 2026-11-17).
     */
    private fun assertTheHandNamesStandByTheirKeys(width: Int, height: Int = 653, locale: Locale = Locale.ENGLISH) {
        show(FingerPick(7), locale, width = width, height = height)
        val croatian = locale.language == "hr"
        val chart = bounds(if (croatian) "Hvat za g1" else "Fingering for G4")
        val unit = (chart.right - chart.left) / FingeringGeometry.WIDTH
        val leftName = compose.onAllNodesWithText(if (croatian) "Lijeva ruka" else "Left hand", useUnmergedTree = true)[0]
        val rightName = compose.onAllNodesWithText(if (croatian) "Desna ruka" else "Right hand", useUnmergedTree = true)[0]
        val left = leftName.getUnclippedBoundsInRoot()
        val right = rightName.getUnclippedBoundsInRoot()
        fun inside(name: String, at: DpRect, span: ClosedFloatingPointRange<Float>) {
            val from = chart.left + unit * span.start
            val to = chart.left + unit * span.endInclusive
            assertTrue("$width wide: $name is at ${at.left} to ${at.right}, its bracket at $from to $to", at.left >= from - SLACK && at.right <= to + SLACK)
            assertTrue("$width wide: $name starts at ${at.top}, the chart ends at ${chart.bottom}", at.top >= chart.bottom - SLACK)
        }
        inside("the left hand's name", left, FingeringGeometry.LEFT_HAND_SPAN)
        inside("the right hand's name", right, FingeringGeometry.RIGHT_HAND_SPAN)
        assertTrue("the two names are apart: ${left.right} and ${right.left}", left.right <= right.left)
        if (croatian) {
            assertWhole("Lijeva ruka", leftName)
            assertWhole("Desna ruka", rightName)
        }
    }

    @Test
    fun the_hand_names_stand_by_their_keys_on_a_narrow_phone() = assertTheHandNamesStandByTheirKeys(width = 360)

    @Test
    fun the_hand_names_stand_by_their_keys_on_the_a55() = assertTheHandNamesStandByTheirKeys(width = 411)

    /** The 7-inch tablet's upright tab, as it is laid out before the zoom (`TabletSize.EIGHT_UPRIGHT`: 560 x 708). */
    @Test
    fun the_hand_names_stand_by_their_keys_on_a_tablets_wide_row() = assertTheHandNamesStandByTheirKeys(width = 560, height = 708)

    /** The Croatian names are the longer ones. */
    @Test
    fun in_croatian_on_a_narrow_phone_the_hand_names_stand_under_their_keys() =
        assertTheHandNamesStandByTheirKeys(width = 360, locale = Locale("hr"))

    /**
     * In a pane narrower than any phone (a third of a foldable) the names cannot keep to their brackets, but they stay
     * inside the chart's width (the final review, 2026-11-24: the right one ran past the chart's end).
     */
    @Test
    fun in_a_narrow_pane_the_hand_names_stay_inside_the_chart() {
        show(FingerPick(7), width = 250)
        val chart = bounds("Fingering for G4")
        listOf("Left hand", "Right hand").forEach {
            val name = compose.onAllNodesWithText(it, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
            assertTrue("$it is at ${name.left} to ${name.right}, the chart at ${chart.left} to ${chart.right}",
                name.left >= chart.left - SLACK && name.right <= chart.right + SLACK)
        }
    }

    /** On the user's phone the staff has its full size (an 8.2 dp line gap) and the four rows of C open without scrolling. */
    @Test
    fun on_the_a55_the_staff_has_its_full_size_and_four_rows_fit() {
        show(FingerPick(), width = 411, height = 653)
        val staff = compose.onAllNodesWithTag("fingers_staff", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals("the staff's height", SymbolStaff.TALL, (staff.bottom - staff.top).value, 0.5f)
        val room = compose.onNodeWithTag("fingers_rows").getUnclippedBoundsInRoot()
        compose.onAllNodesWithTag("fingers_row").assertCountEquals(4)
        (0 until 4).forEach { i ->
            val row = compose.onAllNodesWithTag("fingers_row")[i].getUnclippedBoundsInRoot()
            assertTrue("row $i $row is inside $room", row.isInside(room))
        }
    }

    /**
     * As on the flute tab, a key acts as the finger goes down and not when it lifts (the review of 2026-11-17: these
     * keys were built a second time and waited for the lift).
     */
    @Test
    fun a_key_acts_as_the_finger_goes_down() {
        show(FingerPick())
        compose.onNodeWithContentDescription(SIGN).performTouchInput { down(center) }
        compose.runOnIdle { assertEquals("the sign key, finger down", 1, signs) }
        compose.onNodeWithContentDescription(SIGN).performTouchInput { up() }
        compose.onNodeWithContentDescription("D").performTouchInput { down(center) }
        compose.runOnIdle { assertEquals("D, finger down", listOf(1), letters) }
        compose.onNodeWithContentDescription("D").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals("the lift adds nothing", 1, signs)
            assertEquals("the lift adds nothing", listOf(1), letters)
        }
    }

    @Test
    fun croatian_writes_h_and_names_the_hands() {
        show(FingerPick(11), Locale("hr"))
        compose.onNodeWithContentDescription("H").assertIsSelected()
        compose.onAllNodesWithText("Lijeva ruka", useUnmergedTree = true)[0].assertIsDisplayed()
        compose.onAllNodesWithText("Desna ruka", useUnmergedTree = true)[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Hvat za h1").assertExists()
    }

    /** The pill has its own line over the chart since the names went under it (2026-11-24). */
    @Test
    fun on_a_narrow_phone_in_croatian_the_pill_stands_over_the_chart() {
        show(FingerPick(8), Locale("hr"), width = 360)
        listOf("gis1", "gis2", "gis3").forEachIndexed { i, name ->
            val pill = compose.onNodeWithText(name, useUnmergedTree = true)
            val chart = bounds("Hvat za $name")
            assertTrue("$name ends at ${pill.getUnclippedBoundsInRoot().bottom}, its chart starts at ${chart.top}",
                pill.getUnclippedBoundsInRoot().bottom <= chart.top + SLACK)
            assertWhole("$name", pill)
            assertWhole("Lijeva ruka $i", compose.onAllNodesWithText("Lijeva ruka", useUnmergedTree = true)[i])
            assertWhole("Desna ruka $i", compose.onAllNodesWithText("Desna ruka", useUnmergedTree = true)[i])
            val row = compose.onAllNodesWithTag("fingers_row")[i].getUnclippedBoundsInRoot()
            assertTrue("$name's chart is inside its row", chart.isInside(row))
        }
    }

    @Test
    fun signed_names_stay_whole_on_the_narrowest_keys_at_the_largest_text() {
        show(FingerPick(0, true), Locale("hr"), width = 360, fontScale = 2f)
        listOf("Cis", "Es", "Fis", "Gis", "B").forEach {
            assertWhole(it, compose.onNodeWithText(it, useUnmergedTree = true), 2f)
        }
        val keyRow = compose.onNodeWithTag("fingers_keys").getUnclippedBoundsInRoot()
        listOf("Cis", "D", "Es", "Fis", "Gis", "A", "B", SIGN_HR).forEach {
            assertTrue("$it is inside the keys' row", bounds(it).isInside(keyRow))
        }
    }

    @Test
    fun on_a_short_screen_the_rows_scroll_and_the_keys_stay() {
        show(FingerPick(), height = 480)
        // Against the forced height, so the check does not lean on the test window's own size.
        val keyRow = compose.onNodeWithTag("fingers_keys").getUnclippedBoundsInRoot()
        assertTrue("the keys end at ${keyRow.bottom}, the screen at 480", keyRow.bottom <= 480.dp + SLACK)
        compose.onNodeWithContentDescription("Fingering for C7").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_words_do_not_grow_with_the_text_size() {
        show(FingerPick(9))
        val before = compose.onAllNodesWithText("Left hand", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        compose.runOnIdle { scale = 2f }
        compose.waitForIdle()
        val after = compose.onAllNodesWithText("Left hand", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals((before.right - before.left).value, (after.right - after.left).value, 1f)
        assertEquals((before.bottom - before.top).value, (after.bottom - after.top).value, 1f)
    }

    private companion object {
        const val SIGN_HR = "Povisilica ili snizilica za sljedeću notu"
        val SLACK = 1.dp
    }
}
