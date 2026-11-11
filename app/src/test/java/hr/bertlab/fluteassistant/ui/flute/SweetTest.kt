// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.flute.Songbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SweetTest {

    @Test
    fun `every book on the shelf has its own sweet`() {
        assertEquals(Songbook.SIZE, Sweet.entries.size)
    }

    @Test
    fun `the sweets keep the shelf's order`() {
        assertEquals(listOf("🍭", "🍪", "🧁", "🍩", "🍦", "🍬"), Sweet.entries.map { it.emoji })
    }

    @Test
    fun `the sweet is about a third of its cover's height, never under 40 dp nor over half its width`() {
        // A tall phone's cover (412 dp wide phone): a third of the height, as the mockup draws it.
        assertEquals(68.8f, sweetSize(width = 143.dp, height = 215.dp).value, 0.01f)
        // The shortest cover keeps the sweet it always had.
        assertEquals(40.dp, sweetSize(width = 143.dp, height = 116.dp))
        // A narrow cover bounds it, so it never crowds the spine or the label.
        assertEquals(50.dp, sweetSize(width = 100.dp, height = 300.dp))
    }

    @Test
    fun `the sideways sweet is the mockup's 58 dp on the widest books and 47 on the next`() {
        // 915 x 411: 92.8 dp of room on a 300 dp cover. Upright's half of the width gave 46 here.
        assertEquals(58f, sidewaysSweetSize(width = 92.8.dp, height = 300.dp).value, 0.1f)
        assertEquals(46.4f, sweetSize(width = 92.8.dp, height = 300.dp).value, 0.1f)
        // 800 x 360: 75 dp of room.
        assertEquals(46.9f, sidewaysSweetSize(width = 75.dp, height = 300.dp).value, 0.1f)
    }

    @Test
    fun `the sideways sweet on the narrowest books keeps 40 dp`() {
        // 640 x 360: 48.3 dp of room, where a share of the width alone gives 30 dp.
        assertEquals(40.dp, sidewaysSweetSize(width = 48.3.dp, height = 300.dp))
    }

    /**
     * Every sideways window is at least 600 dp wide, which leaves each of the six books 41.7 dp of
     * room, so the sweet never falls under the book button's: the bound holds from about 31 dp up,
     * not only on the 640 wide phone, and for every cover height.
     */
    @Test
    fun `the sideways sweet never falls under the book button's in any sideways window`() {
        for (width in generateSequence(41.7f) { it + 0.5f }.takeWhile { it <= 120f }) {
            for (height in listOf(100f, 200f, 300f, 400f)) {
                val sweet = sidewaysSweetSize(width = width.dp, height = height.dp)
                assertTrue("$width x $height gives $sweet", sweet >= CornerSweetSize)
            }
        }
        // The least room that keeps it over: 0.85 of the width is the book button's 26 dp.
        assertTrue(sidewaysSweetSize(width = 31.dp, height = 300.dp) >= CornerSweetSize)
        assertTrue(sidewaysSweetSize(width = 30.dp, height = 300.dp) < CornerSweetSize)
    }

    @Test
    fun `a sideways sweet is no bigger than a third of its cover's height`() {
        assertEquals(40.dp, sidewaysSweetSize(width = 200.dp, height = 100.dp))
        assertEquals(64f, sidewaysSweetSize(width = 200.dp, height = 200.dp).value, 0.01f)
    }

    @Test
    fun `a sideways label shows as many of the first notes as fit`() {
        // A 22 dp bubble takes 24 with its gap, and the label's padding and outline take 10 more.
        // The 915 x 411 phone's label (about 92 dp) holds three, as the mockup draws it.
        assertEquals(3, coverNotesThatFit(90.dp))
        // Five fit exactly at 128 dp, the upright label's width.
        assertEquals(5, coverNotesThatFit(128.dp))
        // One short of five stays at four.
        assertEquals(4, coverNotesThatFit(127.dp))
        // Never none, even on a label too narrow for one bubble.
        assertEquals(1, coverNotesThatFit(20.dp))
    }

    @Test
    fun `a sideways label never shows more notes than a book's cover holds`() {
        assertEquals(Songbook.COVER_NOTES, coverNotesThatFit(400.dp))
    }
}
