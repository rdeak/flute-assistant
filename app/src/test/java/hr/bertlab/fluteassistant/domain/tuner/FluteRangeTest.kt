// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluteRangeTest {

    @Test
    fun `range covers c1 to c4`() {
        assertEquals(60..96, FluteRange.midiRange)
        assertEquals(37, FluteRange.midiRange.count())
        assertTrue(FluteRange.DEFAULT_TARGET in FluteRange.midiRange)
    }

    @Test
    fun `clamp keeps a note inside the range`() {
        assertEquals(60, FluteRange.clamp(10))
        assertEquals(96, FluteRange.clamp(200))
        assertEquals(69, FluteRange.clamp(69))
    }
}
