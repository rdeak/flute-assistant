// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.domain.tuner.FluteKey
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

/** The Fingers tab upright (spec `2026-11-17-fingers-tab-design.md`, part 3). */
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

    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()


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
        compose.onAllNodesWithText("Left hand").assertCountEquals(4)
        compose.onAllNodesWithText("Right hand").assertCountEquals(4)
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
     * The hands' names stand by the keys they name whatever the row's width: "Left hand" over the left hand's keys,
     * from its first finger's cup to the little finger's G sharp lever, and "Right hand" under the right hand's, from
     * its first finger's cup to the little finger's E flat key (the review of 2026-11-17: pinned to the row's ends, on
     * a tablet's wider row "Right hand" stood under the foot joint and "Left hand" by the lip plate).
     */
    private fun assertTheHandNamesStandByTheirKeys(width: Int, height: Int = 653) {
        show(FingerPick(7), width = width, height = height)
        val flute = bounds("Fingering for G4")
        val unit = (flute.right - flute.left) / FingeringGeometry.WIDTH
        fun leftOf(key: FluteKey) = FingeringGeometry.KEYS.getValue(key).let { flute.left + unit * (it.x - it.width / 2) }
        fun rightOf(key: FluteKey) = FingeringGeometry.KEYS.getValue(key).let { flute.left + unit * (it.x + it.width / 2) }
        val left = compose.onAllNodesWithText("Left hand")[0].getUnclippedBoundsInRoot()
        val right = compose.onAllNodesWithText("Right hand")[0].getUnclippedBoundsInRoot()
        assertTrue(
            "$width wide: \"Left hand\" is at ${left.left} to ${left.right}, its keys at ${leftOf(FluteKey.L1)} to ${rightOf(FluteKey.G_SHARP)}",
            left.left >= leftOf(FluteKey.L1) - SLACK && left.right <= rightOf(FluteKey.G_SHARP) + SLACK,
        )
        assertTrue(
            "$width wide: \"Right hand\" is at ${right.left} to ${right.right}, its keys at ${leftOf(FluteKey.R1)} to ${rightOf(FluteKey.E_FLAT)}",
            right.left >= leftOf(FluteKey.R1) - SLACK && right.right <= rightOf(FluteKey.E_FLAT) + SLACK,
        )
        assertTrue("\"Left hand\" is over the flute", left.bottom <= flute.top + SLACK)
        assertTrue("\"Right hand\" is under the flute", right.top >= flute.bottom - SLACK)
    }

    @Test
    fun the_hand_names_stand_by_their_keys_on_a_narrow_phone() = assertTheHandNamesStandByTheirKeys(width = 360)

    @Test
    fun the_hand_names_stand_by_their_keys_on_the_a55() = assertTheHandNamesStandByTheirKeys(width = 411)

    /** The 7-inch tablet's upright tab, as it is laid out before the zoom (`TabletSize.EIGHT_UPRIGHT`: 560 x 708). */
    @Test
    fun the_hand_names_stand_by_their_keys_on_a_tablets_wide_row() = assertTheHandNamesStandByTheirKeys(width = 560, height = 708)

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
        compose.onAllNodesWithText("Lijeva ruka")[0].assertIsDisplayed()
        compose.onAllNodesWithText("Desna ruka")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Hvat za h1").assertExists()
    }

    @Test
    fun on_a_narrow_phone_in_croatian_the_pill_and_the_hand_name_do_not_meet() {
        show(FingerPick(8), Locale("hr"), width = 360)
        listOf("gis1", "gis2", "gis3").forEachIndexed { i, name ->
            val pill = compose.onNodeWithText(name)
            val left = compose.onAllNodesWithText("Lijeva ruka")[i]
            val right = compose.onAllNodesWithText("Desna ruka")[i]
            assertTrue("$name ends at ${pill.getUnclippedBoundsInRoot().right}, its hand starts at ${left.getUnclippedBoundsInRoot().left}",
                pill.getUnclippedBoundsInRoot().right <= left.getUnclippedBoundsInRoot().left)
            assertWhole("$name", pill)
            assertWhole("Lijeva ruka $i", left)
            assertWhole("Desna ruka $i", right)
            val row = compose.onAllNodesWithTag("fingers_row")[i].getUnclippedBoundsInRoot()
            assertTrue("$name's flute is inside its row", bounds("Hvat za $name").isInside(row))
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
        val before = compose.onAllNodesWithText("Left hand")[0].getUnclippedBoundsInRoot()
        compose.runOnIdle { scale = 2f }
        compose.waitForIdle()
        val after = compose.onAllNodesWithText("Left hand")[0].getUnclippedBoundsInRoot()
        assertEquals((before.right - before.left).value, (after.right - after.left).value, 1f)
        assertEquals((before.bottom - before.top).value, (after.bottom - after.top).value, 1f)
    }

    private companion object {
        const val SIGN_HR = "Povisilica ili snizilica za sljedeću notu"
        val SLACK = 1.dp
    }
}
