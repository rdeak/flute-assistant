// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import hr.bertlab.fluteassistant.ui.flute.FluteKeysMinHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sideways layout's numbers (spec `2026-11-05-landscape-design.md`) at the three phones it was
 * drawn for: 915 x 411 (rail 92, so 823 of content), 800 x 360 (716) and 640 x 360 (556).
 * Lengths are checked to within half a dp; the two scales are ratios near 1, where half a unit
 * would let any value through, so they are checked to a hundredth.
 */
class SidewaysGeometryTest {

    private fun assertDp(expected: Float, actual: Float) = assertEquals(expected, actual, 0.5f)

    private fun assertScale(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun `a wide window is sideways, a tall one is not`() {
        assertTrue(SidewaysGeometry.isSideways(915f, 411f))
        assertFalse(SidewaysGeometry.isSideways(411f, 915f))
    }

    @Test
    fun `under 600 wide stays upright even when wider than tall, 600 is sideways`() {
        assertFalse(SidewaysGeometry.isSideways(457f, 411f))
        assertFalse(SidewaysGeometry.isSideways(599f, 300f))
        assertTrue(SidewaysGeometry.isSideways(600f, 300f))
    }

    @Test
    fun `a square window is not sideways`() {
        assertFalse(SidewaysGeometry.isSideways(700f, 700f))
    }

    @Test
    fun `the rail and the column are narrower under 400 tall`() {
        assertDp(92f, SidewaysGeometry.railWidth(411f))
        assertDp(84f, SidewaysGeometry.railWidth(360f))
        assertDp(116f, SidewaysGeometry.columnWidth(411f))
        assertDp(100f, SidewaysGeometry.columnWidth(360f))
    }

    @Test
    fun `a window under 400 tall is short`() {
        assertFalse(SidewaysGeometry.isShort(400f))
        assertTrue(SidewaysGeometry.isShort(399f))
    }

    @Test
    fun `400 tall still has the wide rail and column, 399 the narrow ones`() {
        assertDp(92f, SidewaysGeometry.railWidth(400f))
        assertDp(84f, SidewaysGeometry.railWidth(399f))
        assertDp(116f, SidewaysGeometry.columnWidth(400f))
        assertDp(100f, SidewaysGeometry.columnWidth(399f))
    }

    @Test
    fun `the card is the content less the column and three margins`() {
        assertDp(683f, SidewaysGeometry.cardWidth(823f, 411f))
        assertDp(592f, SidewaysGeometry.cardWidth(716f, 360f))
        assertDp(432f, SidewaysGeometry.cardWidth(556f, 360f))
    }

    @Test
    fun `the keys are 372 wide, 304 below 630 of content`() {
        assertDp(372f, SidewaysGeometry.keysWidth(823f))
        assertDp(372f, SidewaysGeometry.keysWidth(716f))
        assertDp(304f, SidewaysGeometry.keysWidth(556f))
        assertDp(372f, SidewaysGeometry.keysWidth(630f))
        assertDp(304f, SidewaysGeometry.keysWidth(629f))
    }

    @Test
    fun `the key gap is 6, 4 below 630 of content`() {
        assertDp(6f, SidewaysGeometry.keyGap(716f))
        assertDp(4f, SidewaysGeometry.keyGap(556f))
        assertDp(6f, SidewaysGeometry.keyGap(630f))
        assertDp(4f, SidewaysGeometry.keyGap(629f))
    }

    @Test
    fun `a key is a third of what the height leaves, its shadow less, from 58 to 88`() {
        assertDp(85.3f, SidewaysGeometry.keyHeight(411f))
        assertDp(68.3f, SidewaysGeometry.keyHeight(360f))
        assertDp(58f, SidewaysGeometry.keyHeight(300f))
        assertDp(88f, SidewaysGeometry.keyHeight(800f))
    }

    @Test
    fun `the running staff is 4 over 3 where it fits and the largest that fits below`() {
        assertScale(4f / 3f, SidewaysGeometry.runningStaffScale(411f))
        assertScale(1.19f, SidewaysGeometry.runningStaffScale(360f))
    }

    @Test
    fun `the ring is 304 at most, then what the card's height or half its width leaves`() {
        assertDp(304f, SidewaysGeometry.ringSize(683f, 411f))
        assertDp(277f, SidewaysGeometry.ringSize(592f, 360f))
        assertDp(197f, SidewaysGeometry.ringSize(432f, 360f))
    }

    /** The three phones never reach it: a wide card on a window under 360 tall has the height's inside, 246 at 300. */
    @Test
    fun `the ring is the card's inside height where that is less than half the width and 304`() {
        assertDp(246f, SidewaysGeometry.ringSize(800f, 300f))
    }

    @Test
    fun `the big beats are 16 apart up to four beats, 10 for more`() {
        assertDp(16f, SidewaysGeometry.bigBeatGap(2))
        assertDp(16f, SidewaysGeometry.bigBeatGap(4))
        assertDp(10f, SidewaysGeometry.bigBeatGap(5))
        assertDp(10f, SidewaysGeometry.bigBeatGap(6))
    }

    @Test
    fun `the big beats fill a wide stage`() {
        assertDp(143.5f, SidewaysGeometry.bigBeatUnit(645f, 4))
        assertDp(96.6f, SidewaysGeometry.bigBeatUnit(645f, 6))
        assertDp(121.6f, SidewaysGeometry.bigBeatUnit(554f, 4))
    }

    @Test
    fun `the big beats shrink on a narrow stage and stop at 150 on a huge one`() {
        assertDp(83.2f, SidewaysGeometry.bigBeatUnit(394f, 4))
        assertDp(55.8f, SidewaysGeometry.bigBeatUnit(394f, 6))
        assertDp(150f, SidewaysGeometry.bigBeatUnit(2000f, 4))
        assertDp(150f, SidewaysGeometry.BIG_BEAT_MAX)
    }

    @Test
    fun `the pitch window grows with the card's height`() {
        assertScale(1.19f, SidewaysGeometry.pitchWindowScale(411f))
        assertScale(1.02f, SidewaysGeometry.pitchWindowScale(360f))
    }

    @Test
    fun `the fingering stands beside the window when 205 dp is left for it`() {
        assertTrue(SidewaysGeometry.fingeringBeside(683f, 411f))
        assertTrue(SidewaysGeometry.fingeringBeside(592f, 360f))
        assertFalse(SidewaysGeometry.fingeringBeside(432f, 360f))
    }

    /**
     * The arrows' 20 dp gap (upright's) is what the layout draws, so it is what the decision counts:
     * the window block at 360 tall is 2 * 52 + 2 * 20 + 132 * 1.02 = 278.6, and with the card's 38,
     * the fingering's gap of 20 and its 205 the card must be 541.6. A 14 dp gap would put the edge at 529.6.
     */
    @Test
    fun `the fingering's edge at 360 tall is a card of 542 dp`() {
        assertTrue(SidewaysGeometry.fingeringBeside(542f, 360f))
        assertFalse(SidewaysGeometry.fingeringBeside(541f, 360f))
    }

    /** The same at 411 tall: the window is 157.1 wide, so the edge is 564.1. */
    @Test
    fun `the fingering's edge at 411 tall is a card of 565 dp`() {
        assertTrue(SidewaysGeometry.fingeringBeside(565f, 411f))
        assertFalse(SidewaysGeometry.fingeringBeside(564f, 411f))
    }

    /** The card a window [width] wide and [height] tall leaves, with the rail taken off as `MainScreen` does. */
    private fun cardOf(width: Float, height: Float) = SidewaysGeometry.cardWidth(width - SidewaysGeometry.railWidth(height), height)

    /**
     * A camera hole takes width from the tab (the emulator's takes 45 at 800 x 360). The edge is a
     * window 750 wide at 360 tall, which an 800 wide phone keeps with a hole up to 50 dp and a 780
     * wide one with a hole up to 30. Under 750 the fingering goes under the window.
     */
    @Test
    fun `at 360 tall a window 750 wide has the fingering beside the window, 740 does not`() {
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(750f, 360f), 360f))
        assertFalse(SidewaysGeometry.fingeringBeside(cardOf(740f, 360f), 360f))
        assertFalse(SidewaysGeometry.fingeringBeside(cardOf(640f, 360f), 360f))
    }

    @Test
    fun `the 780, 800 and 915 wide phones keep the fingering beside the window, with a camera hole of 30 dp too`() {
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(780f, 360f), 360f))
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(800f, 360f), 360f))
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(915f, 360f), 360f))
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(915f, 411f), 411f))
        // The hole takes its width off the tab, in either sideways direction.
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(780f - 30f, 360f), 360f))
        assertTrue(SidewaysGeometry.fingeringBeside(cardOf(800f - 45f, 360f), 360f))
    }

    @Test
    fun `the fingering is 324 wide beside the window on the 915 wide phone, and 255 on the 800 wide one`() {
        assertDp(324f, SidewaysGeometry.fingeringRoom(683f, 411f))
        assertDp(255f, SidewaysGeometry.fingeringRoom(592f, 360f))
    }

    @Test
    fun `the arrows are 52 dp with a gap of 20 either side of the window`() {
        assertDp(52f, SidewaysGeometry.ARROW_SIZE)
        assertDp(20f, SidewaysGeometry.ARROW_GAP)
        assertDp(20f, SidewaysGeometry.FINGERING_GAP)
    }

    /** The page needs its two margins, the keys at their least, the gap, and Play with its shadow: 16 + 213 + 8 + 92. */
    @Test
    fun `writing needs 329 dp, which is the keys' least height and the buttons`() {
        assertDp(329f, SidewaysGeometry.WRITING_MIN_HEIGHT)
        assertDp(2 * SidewaysGeometry.MARGIN + FluteKeysMinHeight.value + 8f + 92f, SidewaysGeometry.WRITING_MIN_HEIGHT)
        assertDp(313f, SidewaysGeometry.WRITING_MIN_HEIGHT - 2 * SidewaysGeometry.MARGIN)
    }

    /** Where the keys fit the formula's own height is the least a key has, so nothing is lost by the scroll below it. */
    @Test
    fun `a key is its least at the height where writing stops fitting`() {
        assertDp(58f, SidewaysGeometry.keyHeight(SidewaysGeometry.WRITING_MIN_HEIGHT))
        assertTrue(SidewaysGeometry.keyHeight(SidewaysGeometry.WRITING_MIN_HEIGHT + 30f) > 58f)
    }

    /**
     * Pick's dots, 3 dp apart, keep 9 dp from each side of the column. The usual five are 11 dp on
     * both columns; seven letters (all fifteen notes) are 11 on the 116 column and 9 on the 100 one.
     */
    @Test
    fun `seven dots are 11 dp on the 116 column and 9 on the 100 column`() {
        assertEquals(11f, SidewaysGeometry.pickDotSize(116f, 7), 0f)
        assertEquals(9f, SidewaysGeometry.pickDotSize(100f, 7), 0f)
    }

    @Test
    fun `six or fewer dots are 11 dp on every column`() {
        assertEquals(11f, SidewaysGeometry.pickDotSize(100f, 6), 0f)
        assertEquals(11f, SidewaysGeometry.pickDotSize(100f, 5), 0f)
        assertEquals(11f, SidewaysGeometry.pickDotSize(116f, 1), 0f)
        assertEquals(11f, SidewaysGeometry.pickDotSize(100f, 1), 0f)
    }

    /** Whatever the size, the dots and their gaps leave 9 dp either side of the column. */
    @Test
    fun `the dots never come within 9 dp of the column's sides`() {
        listOf(100f, 116f).forEach { column ->
            (1..7).forEach { letters ->
                val row = letters * SidewaysGeometry.pickDotSize(column, letters) + 3f * (letters - 1)
                assertTrue("$letters dots in $column: $row", (column - row) / 2 >= 9f)
            }
        }
    }
}
