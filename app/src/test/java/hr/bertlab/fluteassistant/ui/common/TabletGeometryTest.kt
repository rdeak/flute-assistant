// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import hr.bertlab.fluteassistant.ui.common.TabletGeometry.hidesSystemBars
import hr.bertlab.fluteassistant.ui.common.TabletGeometry.isTablet
import hr.bertlab.fluteassistant.ui.common.TabletGeometry.sidewaysKeys
import hr.bertlab.fluteassistant.ui.common.TabletGeometry.sidewaysStaffScale
import hr.bertlab.fluteassistant.ui.common.TabletGeometry.tabZoom
import hr.bertlab.fluteassistant.ui.common.TabletGeometry.uprightKeyHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet's numbers (spec `2026-11-06-tablet-design.md`). The tab areas are the real ones of
 * Google's test sizes, after the bars: 800 x 1065 and 640 x 809 upright, 1188 x 752 and 932 x 592
 * sideways. The zoom is a ratio near 1, so it is checked to a hundredth.
 */
class TabletGeometryTest {

    private fun assertScale(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun `a tablet's shortest side is at least 600`() {
        assertTrue(isTablet(800f, 1280f))
        assertTrue(isTablet(1024f, 640f))
        assertTrue(isTablet(600f, 600f))
        assertFalse(isTablet(915f, 411f))
        assertFalse(isTablet(599f, 1000f))
    }

    @Test
    fun `the zoom at the spec's sizes`() {
        assertScale(1.43f, tabZoom(800f, 1065f, sideways = false, tablet = true))
        assertScale(1.43f, tabZoom(1188f, 752f, sideways = true, tablet = true))
        assertScale(1.14f, tabZoom(640f, 809f, sideways = false, tablet = true))
        assertScale(1.12f, tabZoom(932f, 592f, sideways = true, tablet = true))
        assertScale(1.70f, tabZoom(1508f, 852f, sideways = true, tablet = true))
    }

    @Test
    fun `a foldable and a split pane too short to grow stay at 1`() {
        assertScale(1f, tabZoom(749f, 653f, sideways = true, tablet = true))
        assertScale(1f, tabZoom(640f, 585f, sideways = false, tablet = true))
    }

    @Test
    fun `the zoom stops at 2`() {
        assertScale(2f, tabZoom(2400f, 1500f, sideways = true, tablet = true))
    }

    @Test
    fun `a phone is never zoomed`() {
        assertEquals(1f, tabZoom(411f, 700f, sideways = false, tablet = false))
        assertEquals(1f, tabZoom(823f, 363f, sideways = true, tablet = false))
    }

    @Test
    fun `only a phone on its side hides the bars`() {
        assertTrue(hidesSystemBars(sideways = true, tablet = false))
        assertFalse(hidesSystemBars(sideways = true, tablet = true))
        assertFalse(hidesSystemBars(sideways = false, tablet = false))
    }

    @Test
    fun `sideways the keys and the staff are the spec's on both tablets`() {
        assertEquals(336f, sidewaysKeys(1.43f), 0.01f)
        assertEquals(336f, sidewaysKeys(1.12f), 0.01f)
        assertScale(0.91f, sidewaysStaffScale(1.43f))
        assertScale(0.91f, sidewaysStaffScale(1.12f))
    }

    @Test
    fun `barely zoomed, the keys and the staff are no smaller than a phone's`() {
        assertEquals(372f, sidewaysKeys(1.02f) * 1.02f, 0.5f)
        assertEquals(1f, sidewaysStaffScale(1.02f) * 1.02f, 0.01f)
    }

    @Test
    fun `upright keys take a third of what the height spares, from 58 to 72`() {
        assertEquals(72f, uprightKeyHeight(81f), 0.01f)
        assertEquals(66.67f, uprightKeyHeight(26f), 0.01f)
        assertEquals(58f, uprightKeyHeight(0f), 0.01f)
        assertEquals(58f, uprightKeyHeight(-10f), 0.01f)
    }
}
