// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.ui.common.KeyDepth
import hr.bertlab.fluteassistant.ui.common.KeyRingReach
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.Phone
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.common.isInside
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Fingers tab on a phone on its side (spec `2026-11-17-fingers-tab-design.md`, part 3), at the three phones it was drawn for. */
@RunWith(AndroidJUnit4::class)
class FingersSidewaysTest {

    @get:Rule
    val compose = createComposeRule()

    private var phone by mutableStateOf(Phone(640, 360))
    private var pick by mutableStateOf(FingerPick())
    private val tuned = mutableListOf<Int>()

    /** On its side too a tap on a row's flute asks for that note in the tuner (the user, 2026-11-18). */
    @Test
    fun a_tap_on_a_flute_asks_for_its_note_in_the_tuner() {
        show(FingerPick(7)) // G: its rows G4, G5, G6
        compose.onNodeWithContentDescription("Fingering for G5").assertHasClickAction().performClick()
        assertEquals(listOf(79), tuned)
    }

    private fun show(state: FingerPick, at: Phone = Phone(640, 360), locale: Locale = Locale.ENGLISH, fontScale: Float = 1f) {
        pick = state
        phone = at
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = fontScale) {
                    FingersContent(pick, onLetter = {}, onSign = {}, onTune = { tuned += it })
                }
            }
        }
    }

    private fun on(each: Phone) {
        compose.runOnIdle { phone = each }
        compose.waitForIdle()
    }

    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
    private fun card() = compose.onNodeWithTag("fingers_card").getUnclippedBoundsInRoot()


    @Test
    fun four_rows_fit_the_card_on_every_phone() {
        show(FingerPick())
        PHONES.forEach { each ->
            on(each)
            val flutes = listOf("C4", "C5", "C6", "C7").map { bounds("Fingering for $it") }
            flutes.forEach {
                assertTrue("$each: the flute $it is inside the card ${card()}", it.isInside(card()))
                assertTrue("$each: the flute is ${it.right - it.left} wide", it.right - it.left >= 270.dp)
            }
            flutes.zipWithNext().forEach { (upper, lower) -> assertTrue("$each: $upper over $lower", lower.top >= upper.bottom) }
        }
    }

    @Test
    fun the_eight_keys_stand_two_by_two_right_of_the_card() {
        show(FingerPick())
        PHONES.forEach { each ->
            on(each)
            // The tab's room: the forced window less the rail, as SidewaysHost forces it.
            val root = DpRect(0.dp, 0.dp, each.contentWidth.dp, each.height.dp)
            val keys = KEY_NAMES.map { bounds(it) }
            keys.forEach { key ->
                compose.onNodeWithContentDescription(KEY_NAMES[keys.indexOf(key)]).assertIsDisplayed()
                assertTrue("$each: a key $key is inside the screen $root", key.isInside(root))
                assertTrue("$each: a key starts at ${key.left}, the card ends at ${card().right}", key.left >= card().right)
            }
            keys.chunked(2).forEach { (left, right) ->
                // The chosen key (C) sits 3 dp lower, sunk in its ring.
                assertEquals("$each: a pair's top", left.top.value, right.top.value, 3.5f)
                assertTrue("$each: $left left of $right", right.left >= left.right)
            }
            keys.chunked(2).zipWithNext().forEach { (upper, lower) ->
                assertEquals("$each: a column's left", upper[0].left.value, lower[0].left.value, 1f)
                assertTrue("$each: $upper over $lower", lower[0].top >= upper[0].bottom)
            }
        }
    }

    /**
     * A window shorter than any phone on its side (a pane of a split screen, 280 dp): the keys shrink to stay inside
     * the page's margins, the chosen key's ring and the last pair's shadow with them (the review of 2026-11-17: they
     * never went under 58 dp and ran over both ends of a window under about 312).
     */
    @Test
    fun the_keys_shrink_into_a_window_280_tall() {
        show(FingerPick(), Phone(640, 280))
        val keys = KEY_NAMES.map { bounds(it) }
        val margin = SidewaysGeometry.MARGIN.dp
        keys.forEach { key ->
            assertTrue("a key $key and its ring start inside the page", key.top - KeyRingReach >= margin)
            assertTrue("a key $key and its shadow end inside the page", key.bottom + KeyDepth <= 280.dp - margin)
            assertTrue("a key is ${key.bottom - key.top} tall", key.bottom - key.top >= 46.dp - SLACK)
        }
        keys.chunked(2).zipWithNext().forEach { (upper, lower) ->
            assertTrue("$upper and its shadow over $lower", lower[0].top >= upper[0].bottom + KeyDepth)
        }
    }

    /** Shorter still (240 dp), the keys keep their least size and their column scrolls, as the flute's keys do. */
    @Test
    fun the_keys_scroll_in_a_window_240_tall() {
        show(FingerPick(), Phone(640, 240))
        val margin = SidewaysGeometry.MARGIN.dp
        val first = bounds("C")
        assertTrue("C and its ring start inside the page: $first", first.top - KeyRingReach >= margin)
        assertTrue("a key is ${first.bottom - first.top} tall", first.bottom - first.top >= 46.dp - SLACK)
        compose.onNodeWithContentDescription(SIGN).performScrollTo().assertIsDisplayed()
        val last = bounds(SIGN)
        assertTrue("the last key and its shadow end inside the page: $last", last.bottom + KeyDepth <= 240.dp - margin)
    }

    /**
     * On a wider phone the flute is as wide as the row's height allows and stops short of the card's end: the row's
     * name, staff and flute stand in the middle of the card, not left with a blank band on the right.
     */
    @Test
    fun the_rows_stand_in_the_middle_of_the_card() {
        show(FingerPick())
        listOf(Phone(800, 360), Phone(915, 411)).forEach { each ->
            on(each)
            val pill = compose.onAllNodesWithTag("fingers_pill")[0].getUnclippedBoundsInRoot()
            val flute = bounds("Fingering for C4")
            val left = pill.left - card().left
            val right = card().right - flute.right
            assertEquals("$each: the room left of the name ($left) and right of the flute ($right)", left.value, right.value, 2f)
        }
    }

    @Test
    fun the_hands_are_not_named_on_its_side() {
        show(FingerPick())
        compose.onNodeWithText("Left hand").assertDoesNotExist()
        compose.onNodeWithText("Right hand").assertDoesNotExist()
    }

    @Test
    fun three_rows_are_as_tall_as_four_would_be() {
        show(FingerPick())
        val four = bounds("Fingering for C4")
        compose.runOnIdle { pick = FingerPick(9) }
        compose.waitForIdle()
        val three = bounds("Fingering for A4")
        assertEquals((four.bottom - four.top).value, (three.bottom - three.top).value, 1f)
    }

    @Test
    fun croatian_at_the_largest_text_fits() {
        show(FingerPick(8), locale = Locale("hr"), fontScale = 2f)
        listOf("gis1", "gis2", "gis3").forEach { name ->
            assertWhole(name, compose.onNodeWithText(name), 2f)
            assertTrue("$name's flute is inside the card", bounds("Hvat za $name").isInside(card()))
        }
    }

    private companion object {
        val PHONES = listOf(Phone(640, 360), Phone(800, 360), Phone(915, 411))
        val SLACK = 1.dp
    }
}
