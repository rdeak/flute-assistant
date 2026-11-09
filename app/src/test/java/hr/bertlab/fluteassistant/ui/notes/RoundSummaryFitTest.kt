// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the summary gives way to a short stage ([fitSummary]): the trophy first, down to its floor, then the
 * tiles. The emoji are measured as the emulator's font draws them, a line 1.364 times the size; the stages are
 * the upright ones of a 411 dp phone's tab at 713 dp (the design phone), 650 and 623 (the tab's minimum).
 */
class RoundSummaryFitTest {

    private val emojiHeight: (String, Int) -> Dp = { _, size -> (size * 1.364f).dp }

    private fun fit(room: Dp, rows: Int) = fitSummary(room, rows, emojiHeight)

    @Test
    fun `the design phone keeps the rule's trophy and the tall tiles`() {
        assertEquals(SummaryFit(90, 104.dp, fit(442.dp, 1).minHeight), fit(442.dp, 1))
        assertEquals(50, fit(442.dp, 2).trophySize)
        assertEquals(104.dp, fit(442.dp, 2).tileHeight)
        assertTrue(fit(442.dp, 1).minHeight <= 442.dp && fit(442.dp, 2).minHeight <= 442.dp)
    }

    @Test
    fun `a shorter stage takes a smaller trophy, as large as fits`() {
        val shorter = fit(379.dp, 1)
        assertEquals(104.dp, shorter.tileHeight)
        assertTrue("the trophy is ${shorter.trophySize} sp", shorter.trophySize in 37..89)
        assertTrue(shorter.minHeight <= 379.dp)
        // The largest that fits: drawn one sp taller than its size, the trophy that fits is one sp smaller.
        val taller = fitSummary(379.dp, 1) { emoji, size -> emojiHeight(emoji, if (emoji == TROPHY_EMOJI) size + 1 else size) }
        assertEquals(shorter.trophySize - 1, taller.trophySize)
    }

    @Test
    fun `when the smallest trophy is not enough the tiles drop to the sideways height`() {
        val short = fit(345.dp, 1)
        assertEquals(92.dp, short.tileHeight)
        assertTrue("the trophy is ${short.trophySize} sp", short.trophySize >= 36)
        assertTrue(short.minHeight <= 345.dp)
    }

    @Test
    fun `below that the summary scrolls at its smallest sizes`() {
        val tiny = fit(300.dp, 2)
        assertEquals(36, tiny.trophySize)
        assertEquals(92.dp, tiny.tileHeight)
        assertTrue("it scrolls: ${tiny.minHeight}", tiny.minHeight > 300.dp)
    }

    @Test
    fun `the trophy never grows past its rule however tall the stage`() {
        assertEquals(90, fit(2000.dp, 1).trophySize)
        assertEquals(50, fit(2000.dp, 2).trophySize)
        assertEquals(90, fit(2000.dp, 0).trophySize)
    }
}
