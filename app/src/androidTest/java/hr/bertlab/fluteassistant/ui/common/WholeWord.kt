// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * A tab's word, in the bar or the rail, is whole and inside its tab (moved from `SideRailTest` on 2026-11-17, when the
 * bar got five tabs). A word cut short still reads as displayed, so this asks the word's own layout. It also asks that
 * the word stands inside its tab: a large emoji over it can push it onto the tab's edge, where the outline and the
 * rounded clip cut it off although its own layout is whole.
 */
fun SemanticsNodeInteractionsProvider.assertWholeWord(word: String, case: String = "") {
    val node = onNodeWithText(word, useUnmergedTree = true).assertIsDisplayed()
    val layouts = mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertFalse("$case$word is cut short", layouts.single().hasVisualOverflow)
    val tab = onNodeWithText(word).getUnclippedBoundsInRoot()
    val text = node.getUnclippedBoundsInRoot()
    // The open tab's outline is drawn over the edge of its content, so the word must also stay clear of that.
    assertTrue("$case$word starts at ${text.top}, its tab's outline ends at ${tab.top + OutlineWidth}", text.top >= tab.top + OutlineWidth - 1.dp)
    assertTrue("$case$word ends at ${text.bottom}, its tab's outline starts at ${tab.bottom - OutlineWidth}", text.bottom <= tab.bottom - OutlineWidth + 1.dp)
    assertTrue("$case$word starts at ${text.left}, its tab's outline ends at ${tab.left + OutlineWidth}", text.left >= tab.left + OutlineWidth - 1.dp)
    assertTrue("$case$word ends at ${text.right}, its tab's outline starts at ${tab.right - OutlineWidth}", text.right <= tab.right - OutlineWidth + 1.dp)
}
