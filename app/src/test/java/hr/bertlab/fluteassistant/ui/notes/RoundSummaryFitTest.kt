// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the summary, a trophy over the keys of the notes asked over Again since 2026-11-10, fits a stage
 * ([fitSummary]): the trophy gives way, down to its floor, and the keys keep their size; and how large the keys
 * are and how they stand in rows ([summaryKeySize], [summaryKeyRows]). The emoji are measured as the emulator's
 * font draws them, a line 1.364 times the size; the stages are the upright ones of a 411 dp phone's tab at 713 dp
 * (the design phone, 442 dp inside) and 623 (the tab's minimum, 352 inside).
 */
class RoundSummaryFitTest {

    private val emojiHeight: (String, Int) -> Dp = { _, size -> (size * 1.364f).dp }

    /** A key at its largest. */
    private val key = 70.dp

    private fun fit(room: Dp, rows: Int) = fitSummary(room, rows, key, emojiHeight)

    @Test
    fun `the design phone keeps the largest trophy with one row of keys and with two`() {
        assertEquals(90, fit(442.dp, 1).trophySize)
        assertEquals(90, fit(442.dp, 2).trophySize)
        assertTrue(fit(442.dp, 1).minHeight <= 442.dp && fit(442.dp, 2).minHeight <= 442.dp)
    }

    @Test
    fun `a short stage takes a smaller trophy, as large as fits, and nothing scrolls at the tab's minimum`() {
        val short = fit(352.dp, 2)
        assertTrue("the trophy is ${short.trophySize} sp", short.trophySize in 37..89)
        assertTrue(short.minHeight <= 352.dp)
        // The largest that fits: drawn one sp taller than its size, the trophy that fits is one sp smaller.
        val taller = fitSummary(352.dp, 2, key) { emoji, size -> emojiHeight(emoji, if (emoji == TROPHY_EMOJI) size + 1 else size) }
        assertEquals(short.trophySize - 1, taller.trophySize)
    }

    /**
     * A trophy that gives way does not take every last dp: it leaves 16 for the parts to stand apart, or the keys
     * sit right on Again (seen in the first render of ten notes asked on a 653 dp tab). Where even its floor does
     * not leave that, it takes what there is.
     */
    @Test
    fun `a trophy that gives way leaves the parts some air`() {
        val short = fit(352.dp, 2)
        assertTrue("only ${352.dp - short.minHeight} to spare", 352.dp - short.minHeight >= 16.dp)
        val tight = fit(315.dp, 2)
        assertEquals(36, tight.trophySize)
        assertTrue(tight.minHeight <= 315.dp)
    }

    @Test
    fun `below the smallest trophy the summary scrolls at its least height`() {
        val tiny = fit(250.dp, 2)
        assertEquals(36, tiny.trophySize)
        assertTrue("it scrolls: ${tiny.minHeight}", tiny.minHeight > 250.dp)
    }

    @Test
    fun `the trophy never grows past its rule however tall the stage`() {
        assertEquals(90, fit(2000.dp, 1).trophySize)
        assertEquals(90, fit(2000.dp, 2).trophySize)
        assertEquals(90, fit(2000.dp, 0).trophySize)
    }

    @Test
    fun `smaller keys leave the trophy more room`() {
        assertTrue(fitSummary(352.dp, 2, 50.dp, emojiHeight).trophySize > fit(352.dp, 2).trophySize)
    }

    // ---- The keys: the notes are the reward, so they are as large as the stage holds ----

    @Test
    fun `a key is as large as its row has room for, up to the largest, and keeps its shape`() {
        assertEquals(DpSize(62.dp, 70.dp), summaryKeySize(400.dp, 5))
        assertEquals(DpSize(62.dp, 70.dp), summaryKeySize(339.dp, 3))
        // Five on a 411 dp phone's stage, 339 dp inside: (339 - 4 x 8) / 5.
        val five = summaryKeySize(339.dp, 5)
        assertEquals(61.4f, five.width.value, 0.01f)
        assertEquals(61.4f * 70f / 62f, five.height.value, 0.01f)
        // The narrowest sideways column, 288 dp.
        assertEquals(51.2f, summaryKeySize(288.dp, 5).width.value, 0.01f)
    }

    @Test
    fun `the keys stand in rows of up to five, evened out`() {
        fun rows(count: Int) = summaryKeyRows(List(count) { 60 + it }).map { it.size }
        assertEquals(emptyList<Int>(), rows(0))
        assertEquals(listOf(3), rows(3))
        assertEquals(listOf(5), rows(5))
        assertEquals(listOf(3, 3), rows(6))
        assertEquals(listOf(4, 3), rows(7))
        assertEquals(listOf(4, 4), rows(8))
        assertEquals(listOf(5, 4), rows(9))
        assertEquals(listOf(5, 5), rows(10))
        assertEquals(listOf(60, 61, 62, 63, 64, 65), summaryKeyRows(listOf(60, 61, 62, 63, 64, 65)).flatten())
    }

    @Test
    fun `ten notes asked are two rows, five are one`() {
        assertEquals(0, summaryRows(emptySet()))
        assertEquals(1, summaryRows((60..64).toSet()))
        assertEquals(2, summaryRows((60..65).toSet()))
        assertEquals(2, summaryRows((60..69).toSet()))
    }
}
