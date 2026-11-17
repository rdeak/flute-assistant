// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** A box is rounded to whole pixels and its text is not. */
private const val ROUNDING = 1f

/** A note's name in its pill or bubble ("A4", "c1"). */
private val NOTE_NAME = Regex("[A-Ha-h][1-6]")

/**
 * Asserts that the text of [node] is shown whole: not cut, not ellipsized, not past its lines, and
 * broken only between words. At a [textScale] above 1 a note's name is not asked to be whole, as it
 * is set in a fixed size in its pill or bubble and clips at twice the text, on a phone just the same.
 * An empty text (the advice line with nothing to say) passes. Moved here from `TabletFitTest` on
 * 2026-11-14 so the tuner's tests use the same rule.
 */
fun assertWhole(case: String, node: SemanticsNodeInteraction, textScale: Float = 1f) {
    val layouts = mutableListOf<TextLayoutResult>()
    node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val layout = layouts.single()
    val text = layout.layoutInput.text.text
    // Not `hasVisualOverflow`: for a plain `Text` this action hands back a paragraph as wide as the room
    // and a box as wide as the word, which reads as an overflow however short the word is.
    val paragraph = layout.multiParagraph
    if (textScale > 1f && NOTE_NAME.matches(text)) return
    if (text.isBlank()) return
    val wider = layout.lineCount == 1 && paragraph.maxIntrinsicWidth > layout.size.width + ROUNDING
    val taller = paragraph.height > layout.size.height + ROUNDING
    val ellipsized = (0 until layout.lineCount).any(layout::isLineEllipsized)
    assertFalse(
        "$case: \"$text\" is cut short: its box is ${layout.size}, its text ${paragraph.maxIntrinsicWidth} x ${paragraph.height}",
        paragraph.didExceedMaxLines || taller || wider || ellipsized,
    )
    for (line in 0 until layout.lineCount - 1) {
        assertTrue(
            "$case: \"$text\" breaks inside a word after line $line",
            text[layout.getLineEnd(line) - 1].isWhitespace(),
        )
    }
}
