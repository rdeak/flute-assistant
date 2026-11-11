// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SidewaysPageTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(height: Int = 411, column: (@Composable ColumnScope.() -> Unit)?) {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                SidewaysHost(height = height) {
                    Box(Modifier.testTag(PAGE)) {
                        SidewaysPage(column = column) { Box(it.testTag(CARD)) }
                    }
                }
            }
        }
    }

    private fun block(tag: String) = Modifier.testTag(tag).fillMaxWidth().height(40.dp)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** The forced size rounds the density down, which leaves sizes a dp or two off the ones asked for. */
    private fun assertNear(expected: Dp, actual: Dp) =
        assertTrue("expected about $expected, got $actual", abs((expected - actual).value) <= TOLERANCE)

    @Test
    fun the_card_and_the_column_keep_their_margins() {
        show { Box(block(TOP)) }
        val page = bounds(PAGE)
        val card = bounds(CARD)
        val column = bounds(TOP)
        assertNear(8.dp, card.left - page.left)
        assertNear(8.dp, card.top - page.top)
        assertNear(8.dp, page.bottom - card.bottom)
        assertNear(8.dp, column.left - card.right)
        assertNear(8.dp, page.right - column.right)
    }

    @Test
    fun the_column_is_116_wide() {
        show { Box(block(TOP)) }
        assertNear(116.dp, bounds(TOP).right - bounds(TOP).left)
    }

    @Test
    fun the_column_is_100_wide_on_a_short_window() {
        show(height = 360) { Box(block(TOP)) }
        assertNear(100.dp, bounds(TOP).right - bounds(TOP).left)
    }

    @Test
    fun without_a_column_the_card_has_the_room() {
        show(column = null)
        assertNear(8.dp, bounds(PAGE).right - bounds(CARD).right)
    }

    @Test
    fun two_children_sit_at_the_top_and_the_bottom() {
        show {
            Box(block(TOP))
            Box(block(BOTTOM))
        }
        assertNear(bounds(CARD).top + COLUMN_TOP_ROOM, bounds(TOP).top)
        assertNear(bounds(CARD).bottom - COLUMN_SHADOW_ROOM, bounds(BOTTOM).bottom)
    }

    /** Play sits low on every tab, also where the column holds nothing else. */
    @Test
    fun a_lone_child_sits_at_the_bottom() {
        show { Box(block(BOTTOM)) }
        assertNear(bounds(CARD).bottom - COLUMN_SHADOW_ROOM, bounds(BOTTOM).bottom)
    }

    @Test
    fun the_action_button_is_as_wide_as_the_column_and_104_tall() {
        var pressed = 0
        show {
            SidewaysActionButton(AppIcons.Done, "Play", ActionGo, { pressed++ }, Modifier.testTag(BOTTOM))
        }
        assertSize(compose.onNodeWithTag(BOTTOM), width = 116.dp, height = 104.dp)
        compose.onNodeWithText("Play").assertIsDisplayed().performClick()
        assertEquals(1, pressed)
    }

    @Test
    fun the_plain_button_is_as_wide_as_the_column_and_84_tall() {
        show {
            SidewaysPlainButton("🔊", "Hear", {}, Modifier.testTag(BOTTOM))
        }
        assertSize(compose.onNodeWithTag(BOTTOM), width = 116.dp, height = 84.dp)
        compose.onNodeWithText("Hear").assertIsDisplayed()
    }

    @Test
    fun the_plain_button_takes_another_height() {
        show {
            SidewaysPlainButton("🔊", "Hear", {}, Modifier.testTag(BOTTOM), height = 92.dp)
        }
        assertSize(compose.onNodeWithTag(BOTTOM), width = 116.dp, height = 92.dp)
    }

    /** The tuner's short buttons: at large text the emoji leaves the word less than its full line. */
    @Test
    fun a_short_plain_button_keeps_its_word_at_large_text() {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                SidewaysHost(height = 360, fontScale = 1.5f) {
                    SidewaysPage(column = { SidewaysPlainButton("\uD83D\uDD0D", "Which?", {}, height = 80.dp) }) { Box(it) }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Which?", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("the word is cut short", layouts.single().hasVisualOverflow)
    }

    @Test
    fun the_stage_takes_another_padding() {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                Stage(modifier = Modifier.testTag(CARD), padding = 10.dp) { Box(block(TOP)) }
            }
        }
        assertEquals(10f, (bounds(TOP).top - bounds(CARD).top).value, 0.5f)
    }

    @Test
    fun a_plain_button_with_selected_is_one_of_a_group() {
        show {
            SidewaysPlainButton("🔍", "Which?", {}, selected = true)
            SidewaysPlainButton("🎯", "Tune", {}, selected = false)
        }
        compose.onNodeWithText("Which?").assertIsSelected()
        compose.onNodeWithText("Tune").assertIsNotSelected()
    }

    @Test
    fun a_plain_button_can_be_disabled() {
        show {
            SidewaysPlainButton("🔊", "Hear", {}, enabled = false)
        }
        compose.onNodeWithText("Hear").assertIsNotEnabled()
    }

    private fun assertSize(node: SemanticsNodeInteraction, width: Dp, height: Dp) {
        val bounds = node.getUnclippedBoundsInRoot()
        assertNear(width, bounds.right - bounds.left)
        assertNear(height, bounds.bottom - bounds.top)
    }

    private companion object {
        const val PAGE = "page"
        const val CARD = "card"
        const val TOP = "top"
        const val BOTTOM = "bottom"
        const val TOLERANCE = 2f

        /** The mockup's room above the column's first child and, below its last, the big button's shadow. */
        val COLUMN_TOP_ROOM = 6.dp
        val COLUMN_SHADOW_ROOM = 8.dp
    }
}
