// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keys' sizes (2026-11-16, sharps and flats): never a white key under 58 dp or a black one under 48, spare height
 * shows more rows before it grows keys, and once all five rows fit they grow together up to the layout's largest.
 */
class KeysGeometryTest {

    @Test
    fun `a black key is 48 dp to the white key's 58, and in that proportion when larger`() {
        assertEquals(48f, KeysGeometry.black(58f), 0.001f)
        assertEquals(72f * 48f / 58f, KeysGeometry.black(72f), 0.001f)
    }

    @Test
    fun `all five rows take 331 dp at the least size`() = assertEquals(331f, KeysGeometry.allRows(58f), 0.001f)

    @Test
    fun `the least room is the top row and the C4 octave's two rows, whole`() =
        assertEquals(63f + 12f + 53f + 8f + 63f, KeysGeometry.MIN_HEIGHT, 0.001f)

    @Test
    fun `while the five rows do not fit, a white key is 58 and the rows scroll`() {
        assertEquals(58f, KeysGeometry.whiteHeight(284f, 58f), 0.001f)
        assertEquals(58f, KeysGeometry.whiteHeight(295f, 88f), 0.001f)
        assertTrue(KeysGeometry.scrolls(330f))
    }

    @Test
    fun `once they fit, the keys grow to fill the height, up to the layout's largest`() {
        assertEquals(58f, KeysGeometry.whiteHeight(331f, 88f), 0.001f)
        assertFalse(KeysGeometry.scrolls(331f))
        assertEquals(74.97f, KeysGeometry.whiteHeight(410f, 88f), 0.1f)
        assertEquals(410f, KeysGeometry.allRows(KeysGeometry.whiteHeight(410f, 88f)), 0.01f)
        assertEquals(88f, KeysGeometry.whiteHeight(1000f, 88f), 0.001f)
        assertEquals(72f, KeysGeometry.whiteHeight(1000f, 72f), 0.001f)
    }

    @Test
    fun `a black key is centred on the gap between its two white keys`() =
        assertEquals(27f, KeysGeometry.blackLeft(0, 48f, 6f), 0.001f)
}
